#!/usr/bin/env python3
"""Update Korea Diocese Locator remote data from the CBCK online directory.

Safety properties:
- Discovers territorial dioceses from CBCK's own directory index.
- Requires all 15 territorial dioceses to parse successfully before writing.
- Preserves the previous JSON if validation fails.
- Treats a changed jurisdiction description as an app-boundary update signal:
  boundaryVersion is incremented once and the new normalized descriptions are snapshotted.
- Military Ordinariate is intentionally excluded from GPS territorial matching.
"""
from __future__ import annotations

import argparse
import dataclasses
import datetime as dt
import hashlib
import json
import re
import sys
import urllib.parse
import urllib.request
from html import unescape
from pathlib import Path
from typing import Iterable

BASE = "https://directory.cbck.or.kr"
INDEX_URL = f"{BASE}/OnlineAddress/Default.aspx"
EXPECTED_DIOCESES = 15
TERRITORIAL_DIOCESES = {
    "서울대교구",
    "춘천교구",
    "대전교구",
    "인천교구",
    "수원교구",
    "원주교구",
    "의정부교구",
    "대구대교구",
    "부산교구",
    "청주교구",
    "마산교구",
    "안동교구",
    "광주대교구",
    "전주교구",
    "제주교구",
}
USER_AGENT = "KoreaDioceseLocator/1.0 (+CBCK directory updater)"


@dataclasses.dataclass(frozen=True)
class DioceseRecord:
    diocese: str
    ordinary: str
    title: str
    statusLabel: str
    rememberOrdinary: bool
    jurisdiction: str
    sourceUrl: str


def fetch(url: str, timeout: int = 20) -> str:
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT, "Accept": "text/html,*/*"})
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        raw = resp.read()
        charset = resp.headers.get_content_charset() or "utf-8"
    try:
        return raw.decode(charset, errors="replace")
    except LookupError:
        return raw.decode("utf-8", errors="replace")


def html_to_text(html: str) -> str:
    html = re.sub(r"(?is)<script\b.*?</script>", " ", html)
    html = re.sub(r"(?is)<style\b.*?</style>", " ", html)
    html = re.sub(r"(?i)<br\s*/?>", "\n", html)
    html = re.sub(r"(?i)</(?:div|p|li|tr|td|th|h1|h2|h3|span)>", "\n", html)
    text = re.sub(r"(?s)<[^>]+>", " ", html)
    text = unescape(text).replace("\xa0", " ")
    text = re.sub(r"[ \t\r\f\v]+", " ", text)
    text = re.sub(r"\n\s*\n+", "\n", text)
    return text.strip()


def discover_diocese_urls(index_html: str) -> list[tuple[str, str]]:
    """
    CBCK 온라인 주소록에서 대한민국의 15개 지역교구만 찾는다.

    군종교구 및 북한 지역 등 다른 교회 관할이 목록에 추가되더라도
    GPS 기반 지역 판정 대상에는 포함하지 않는다.
    """
    pattern = re.compile(
        r'href=["\'](?P<href>[^"\']*Catholic/Diocese\.aspx\?[^"\']*gyogu=\d+[^"\']*)["\'][^>]*>(?P<label>.*?)</a>',
        re.I | re.S,
    )

    found: dict[str, str] = {}

    for m in pattern.finditer(index_html):
        label = html_to_text(m.group("label")).strip(' "')

        # 대한민국 GPS 판정 대상인 15개 지역교구만 허용한다.
        if label not in TERRITORIAL_DIOCESES:
            continue

        href = unescape(m.group("href"))
        url = urllib.parse.urljoin(BASE + "/OnlineAddress/", href)
        found[label] = url

    # ASP.NET 페이지 구조나 상대경로 표기가 달라진 경우를 위한 fallback.
    if len(found) < EXPECTED_DIOCESES:
        for m in re.finditer(
            r'href=["\'](?P<href>[^"\']*Diocese\.aspx\?[^"\']*gyogu=\d+[^"\']*)["\']',
            index_html,
            re.I,
        ):
            href = unescape(m.group("href"))

            window = index_html[
                max(0, m.start() - 150):
                min(len(index_html), m.end() + 200)
            ]

            window_text = html_to_text(window)

            for diocese_name in TERRITORIAL_DIOCESES:
                if diocese_name in window_text:
                    found[diocese_name] = urllib.parse.urljoin(
                        BASE + "/OnlineAddress/",
                        href,
                    )
                    break

    missing = TERRITORIAL_DIOCESES - set(found)

    if missing:
        raise ValueError(
            "CBCK index discovery failed. Missing territorial dioceses: "
            + ", ".join(sorted(missing))
        )

    # 예상하지 못한 교구가 섞이지 않았는지 한 번 더 방어적으로 확인한다.
    unexpected = set(found) - TERRITORIAL_DIOCESES

    if unexpected:
        raise ValueError(
            "Unexpected dioceses detected: "
            + ", ".join(sorted(unexpected))
        )

    if len(found) != EXPECTED_DIOCESES:
        raise ValueError(
            f"CBCK territorial diocese validation failed: "
            f"expected {EXPECTED_DIOCESES}, got {len(found)}"
        )

    return sorted(found.items())


def _extract_field(text: str, label: str, following_labels: Iterable[str]) -> str:
    # Flatten line breaks for robust extraction but stop at the next known field label.
    flat = re.sub(r"\s+", " ", text)
    stop = "|".join(re.escape(x) for x in following_labels)
    m = re.search(re.escape(label) + r"\s*(.+?)(?=\s+(?:" + stop + r")\s|$)", flat)
    return m.group(1).strip() if m else ""


def normalize_jurisdiction(value: str) -> str:
    value = value.strip()
    # Area figures can be independently corrected without changing the actual canonical jurisdiction.
    value = re.sub(r"\([^()]*[㎢km²2][^()]*\)\s*$", "", value, flags=re.I)
    value = value.replace("·", "·").replace("ㆍ", "·")
    value = re.sub(r"\s+", " ", value)
    return value.strip(" .")


def split_name_and_title(raw: str) -> tuple[str, str]:
    raw = re.sub(r"\s+", " ", raw).strip()
    # Ignore year annotations if CBCK ever emits them on the current line.
    raw = re.sub(r"\s*\(\d{4}년?\)\s*$", "", raw)
    for suffix, title in [("추기경", "추기경"), ("대주교", "대주교"), ("주교", "주교"), ("몬시뇰", "몬시뇰"), ("신부", "신부")]:
        if raw.endswith(suffix):
            return raw[: -len(suffix)].strip(), title
    raise ValueError(f"recognized clerical title not found: {raw!r}")


def parse_diocese_page(expected_name: str, url: str, html: str) -> DioceseRecord:
    text = html_to_text(html)
    flat = re.sub(r"\s+", " ", text)
    if expected_name not in flat:
        raise ValueError(f"page does not contain expected diocese name: {expected_name}")

    acting = re.search(r"교구장\s*직무대행\s+(.+?)(?=\s+(?:역대 교구장|대표주소|관할지역\(한글\)|[A-Z][a-z]+\.|Most Rev\.|Rev\.)|$)", flat)
    ordinary = None
    status = None
    if acting:
        ordinary = acting.group(1).strip()
        status = "교구장 직무대행"
    else:
        current = re.search(r"교구장\s+(.+?)(?=\s+(?:보좌 주교|역대 교구장|대표주소|관할지역\(한글\)|[A-Z][a-z]+\.|Most Rev\.|Rev\.|Archbishop|Bishop|Cardinal)|$)", flat)
        if current:
            ordinary = current.group(1).strip()
            status = "교구장"
    if not ordinary or not status:
        raise ValueError(f"current ordinary not found for {expected_name}")

    name, title = split_name_and_title(ordinary)

    jurisdiction_match = re.search(
        r"관할지역\(한글\)\s+(.+?)(?=\s+관할지역\(영문\)|\s+교구별 최종 수정일|$)",
        flat,
    )
    if not jurisdiction_match:
        raise ValueError(f"Korean jurisdiction not found for {expected_name}")
    jurisdiction = normalize_jurisdiction(jurisdiction_match.group(1))
    if len(jurisdiction) < 3:
        raise ValueError(f"jurisdiction too short for {expected_name}: {jurisdiction!r}")

    return DioceseRecord(
        diocese=expected_name,
        ordinary=name,
        title=title,
        statusLabel=status,
        rememberOrdinary=(status == "교구장"),
        jurisdiction=jurisdiction,
        sourceUrl=url,
    )


def validate(records: list[DioceseRecord]) -> None:
    if len(records) != EXPECTED_DIOCESES:
        raise ValueError(f"expected {EXPECTED_DIOCESES} territorial dioceses, got {len(records)}")
    names = [r.diocese for r in records]
    if len(set(names)) != EXPECTED_DIOCESES:
        raise ValueError("duplicate diocese names detected")
    if any(not r.ordinary or not r.jurisdiction for r in records):
        raise ValueError("empty ordinary/jurisdiction detected")
    # A mass parse failure often produces implausibly identical text.
    if len(set(r.ordinary for r in records)) < EXPECTED_DIOCESES - 1:
        raise ValueError("ordinary values are unexpectedly duplicated")


def read_json(path: Path, default):
    if not path.exists():
        return default
    return json.loads(path.read_text(encoding="utf-8"))


def stable_hash(value: str) -> str:
    return hashlib.sha256(value.encode("utf-8")).hexdigest()[:16]


def update_files(records: list[DioceseRecord], remote_path: Path, snapshot_path: Path) -> tuple[bool, list[dict]]:
    old_remote = read_json(remote_path, {"schemaVersion": 1, "boundaryVersion": 1, "ordinaries": []})
    old_snapshot = read_json(snapshot_path, {"jurisdictions": {}})
    previous_j = old_snapshot.get("jurisdictions", {})

    current_j = {r.diocese: r.jurisdiction for r in records}
    boundary_changes: list[dict] = []
    if previous_j:
        for name in sorted(current_j):
            before = previous_j.get(name)
            after = current_j[name]
            if before is not None and before != after:
                boundary_changes.append({"diocese": name, "before": before, "after": after})

    boundary_version = int(old_remote.get("boundaryVersion", 1))
    if boundary_changes:
        boundary_version += 1

    today = dt.datetime.now(dt.timezone(dt.timedelta(hours=9))).date().isoformat()
    payload = {
        "schemaVersion": 1,
        "boundaryVersion": boundary_version,
        "dataUpdatedAt": today,
        "source": {
            "name": "한국천주교주교회의 온라인 주소록",
            "url": INDEX_URL,
            "checkedAt": dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        },
        "ordinaries": [
            {
                "diocese": r.diocese,
                "ordinary": r.ordinary,
                "title": r.title,
                "statusLabel": r.statusLabel,
                "rememberOrdinary": r.rememberOrdinary,
                "sourceUrl": r.sourceUrl,
            }
            for r in sorted(records, key=lambda x: x.diocese)
        ],
    }
    if boundary_changes:
        payload["boundaryChanges"] = boundary_changes

    # Compare content while ignoring timestamps; do not make daily no-op commits.
    def semantic(obj):
        obj = json.loads(json.dumps(obj, ensure_ascii=False))
        obj.pop("dataUpdatedAt", None)
        if isinstance(obj.get("source"), dict):
            obj["source"].pop("checkedAt", None)
        return obj

    changed = semantic(payload) != semantic(old_remote) or current_j != previous_j
    if changed:
        remote_path.parent.mkdir(parents=True, exist_ok=True)
        snapshot_path.parent.mkdir(parents=True, exist_ok=True)
        remote_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        snapshot_path.write_text(
            json.dumps(
                {
                    "schemaVersion": 1,
                    "updatedAt": today,
                    "jurisdictions": current_j,
                    "hashes": {k: stable_hash(v) for k, v in current_j.items()},
                },
                ensure_ascii=False,
                indent=2,
            ) + "\n",
            encoding="utf-8",
        )
    return changed, boundary_changes


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--remote", default="data/remote_data.json")
    parser.add_argument("--snapshot", default="data/boundaries_snapshot.json")
    parser.add_argument("--fixture-dir", help="Use saved HTML fixtures instead of the network")
    args = parser.parse_args()

    root = Path(__file__).resolve().parents[1]
    remote_path = root / args.remote
    snapshot_path = root / args.snapshot

    try:
        if args.fixture_dir:
            fixture = Path(args.fixture_dir)
            index_html = (fixture / "index.html").read_text(encoding="utf-8")
            discovered = discover_diocese_urls(index_html)
            records = []
            for name, url in discovered:
                slug = re.sub(r"[^0-9A-Za-z가-힣_-]", "_", name)
                html = (fixture / f"{slug}.html").read_text(encoding="utf-8")
                records.append(parse_diocese_page(name, url, html))
        else:
            index_html = fetch(INDEX_URL)
            discovered = discover_diocese_urls(index_html)
            records = [parse_diocese_page(name, url, fetch(url)) for name, url in discovered]

        validate(records)
        changed, boundary_changes = update_files(records, remote_path, snapshot_path)
        print(f"Parsed {len(records)} dioceses; changed={str(changed).lower()}; boundaryChanges={len(boundary_changes)}")
        for c in boundary_changes:
            print(f"BOUNDARY_CHANGE {c['diocese']}: {c['before']} -> {c['after']}")
        return 0
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())

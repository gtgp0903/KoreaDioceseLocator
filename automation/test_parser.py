import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
from update_cbck import parse_diocese_page, split_name_and_title, normalize_jurisdiction


def test_regular_ordinary():
    html = '''<html><body><h2>서울대교구</h2><div>교구장 정순택 베드로 대주교</div>
    <div>Archbishop Peter</div><div>보좌 주교 누구 주교</div>
    <div>관할지역(한글) 서울특별시 전 지역(605㎢)</div><div>관할지역(영문) Seoul</div></body></html>'''
    r = parse_diocese_page("서울대교구", "https://example.test", html)
    assert r.ordinary == "정순택 베드로"
    assert r.title == "대주교"
    assert r.statusLabel == "교구장"
    assert r.rememberOrdinary is True
    assert r.jurisdiction == "서울특별시 전 지역"


def test_acting_ordinary():
    html = '''<html><body><h2>청주교구</h2><div>교구장 직무대행 최광조 프란치스코 신부</div>
    <div>Rev. Francis CHOI</div><div>역대 교구장 아무개 주교</div>
    <div>관할지역(한글) 제천시와 단양군을 제외한 충청북도 전 지역과 세종특별자치시 부강면(5,772㎢)</div>
    <div>관할지역(영문) Chungbuk</div></body></html>'''
    r = parse_diocese_page("청주교구", "https://example.test", html)
    assert r.ordinary == "최광조 프란치스코"
    assert r.title == "신부"
    assert r.statusLabel == "교구장 직무대행"
    assert r.rememberOrdinary is False


def test_helpers():
    assert split_name_and_title("홍길동 바오로 주교") == ("홍길동 바오로", "주교")
    assert normalize_jurisdiction(" 어떤 지역 (1,234㎢) ") == "어떤 지역"


if __name__ == "__main__":
    test_regular_ordinary(); test_acting_ordinary(); test_helpers()
    print("parser tests: OK")

package kr.catholic.dioceselocator

data class Diocese(
    val name: String,
    var ordinary: String,
    var title: String = "주교",
    var statusLabel: String = "교구장",
    var rememberOrdinary: Boolean = true
)

data class Region(
    val adminArea: String?,
    val locality: String?,
    val subLocality: String?,
    val featureName: String?,
    val fullAddress: String
)

sealed interface DioceseMatch {
    data class Found(val diocese: Diocese) : DioceseMatch
    data class Ambiguous(val candidates: List<Diocese>, val reason: String) : DioceseMatch
    data class Unknown(val reason: String) : DioceseMatch
}

data class SearchResult(
    val label: String,
    val match: DioceseMatch,
    val description: String = ""
)

object DioceseRepository {
    private val seoul = Diocese("서울대교구", "정순택 베드로", "대주교")
    private val chuncheon = Diocese("춘천교구", "김주영 시몬")
    private val daejeon = Diocese("대전교구", "김종수 아우구스티노")
    private val incheon = Diocese("인천교구", "정신철 요한 세례자")
    private val suwon = Diocese("수원교구", "이용훈 마티아")
    private val wonju = Diocese("원주교구", "조규만 바실리오")
    private val uijeongbu = Diocese("의정부교구", "손희송 베네딕토")
    private val daegu = Diocese("대구대교구", "조환길 타대오", "대주교")
    private val busan = Diocese("부산교구", "손삼석 요셉")
    private val cheongju = Diocese(
        "청주교구",
        "최광조 프란치스코",
        "신부",
        "교구장 직무대행",
        rememberOrdinary = false
    )
    private val masan = Diocese("마산교구", "이성효 리노")
    private val andong = Diocese("안동교구", "권혁주 요한 크리소스토모")
    private val gwangju = Diocese("광주대교구", "옥현진 시몬", "대주교")
    private val jeonju = Diocese("전주교구", "김선태 사도 요한")
    private val jeju = Diocese("제주교구", "문창우 비오")

    val allDioceses: List<Diocese> = listOf(
        seoul, chuncheon, daejeon, incheon, suwon, wonju, uijeongbu,
        daegu, busan, cheongju, masan, andong, gwangju, jeonju, jeju
    )

    fun match(region: Region): DioceseMatch {
        val p = normalize(region.adminArea)
        val all = normalize(
            listOfNotNull(
                region.adminArea,
                region.locality,
                region.subLocality,
                region.featureName,
                region.fullAddress
            ).joinToString(" ")
        )

        if (p.contains("서울")) return DioceseMatch.Found(seoul)
        if (p.contains("인천")) return DioceseMatch.Found(incheon)
        if (p.contains("대전")) return DioceseMatch.Found(daejeon)
        if (p.contains("광주광역")) return DioceseMatch.Found(gwangju)
        if (p.contains("부산")) return DioceseMatch.Found(busan)
        if (p.contains("울산")) return DioceseMatch.Found(busan)
        if (p.contains("제주")) return DioceseMatch.Found(jeju)
        if (p.contains("전북") || p.contains("전라북")) return DioceseMatch.Found(jeonju)
        if (p.contains("전남") || p.contains("전라남")) return DioceseMatch.Found(gwangju)
        if (p.contains("충남") || p.contains("충청남")) return DioceseMatch.Found(daejeon)

        if (p.contains("세종")) {
            return if (all.contains("부강면")) DioceseMatch.Found(cheongju) else DioceseMatch.Found(daejeon)
        }

        if (p.contains("경기")) {
            val uij = listOf("고양", "구리", "남양주", "동두천", "양주", "의정부", "파주", "연천")
            if (uij.any { all.contains(it) }) return DioceseMatch.Found(uijeongbu)
            if (all.contains("김포") || all.contains("부천")) return DioceseMatch.Found(incheon)
            if (all.contains("시흥") || all.contains("안산")) {
                return DioceseMatch.Ambiguous(
                    listOf(incheon, suwon),
                    "시흥·안산 일부 지역은 인천교구와 수원교구 경계가 나뉩니다. 세부 위치 확인이 필요합니다."
                )
            }
            val suw = listOf("과천", "광명", "광주", "군포", "성남", "수원", "안성", "안양", "여주", "오산", "용인", "의왕", "이천", "평택", "하남", "화성", "양평")
            if (suw.any { all.contains(it) }) return DioceseMatch.Found(suwon)
            return DioceseMatch.Unknown("경기도 내 교구 판정 규칙에 없는 지역입니다.")
        }

        if (p.contains("충북") || p.contains("충청북")) {
            if (all.contains("제천") || all.contains("단양")) return DioceseMatch.Found(wonju)
            return DioceseMatch.Found(cheongju)
        }

        if (p.contains("강원")) {
            val wonjuCities = listOf("삼척", "원주", "태백", "영월", "정선", "횡성")
            if (wonjuCities.any { all.contains(it) }) return DioceseMatch.Found(wonju)
            if (all.contains("동해") || all.contains("평창")) {
                return DioceseMatch.Ambiguous(
                    listOf(chuncheon, wonju),
                    "동해시·평창군은 춘천교구와 원주교구 관할이 일부 나뉩니다. 세부 위치 확인이 필요합니다."
                )
            }
            return DioceseMatch.Found(chuncheon)
        }

        if (p.contains("대구")) return DioceseMatch.Found(daegu)

        if (p.contains("경북") || p.contains("경상북")) {
            val andongAreas = listOf("문경", "상주", "안동", "영주", "봉화", "영덕", "영양", "예천", "울진", "의성", "청송")
            if (andongAreas.any { all.contains(it) }) return DioceseMatch.Found(andong)
            return DioceseMatch.Found(daegu)
        }

        if (p.contains("경남") || p.contains("경상남")) {
            if (all.contains("양산")) return DioceseMatch.Found(busan)
            if (all.contains("김해")) {
                val masanSub = listOf("진영읍", "진례면", "한림면")
                if (masanSub.any { all.contains(it) }) return DioceseMatch.Found(masan)
                if (all.contains("생림면")) {
                    if (all.contains("도요리")) return DioceseMatch.Found(busan)
                    return DioceseMatch.Found(masan)
                }
                return DioceseMatch.Found(busan)
            }
            if (all.contains("밀양")) {
                if (all.contains("하남읍") || all.contains("초동면")) return DioceseMatch.Found(masan)
                return DioceseMatch.Found(busan)
            }
            return DioceseMatch.Found(masan)
        }

        return DioceseMatch.Unknown("대한민국 교구 관할로 판정하지 못했습니다.")
    }

    fun search(query: String): List<SearchResult> {
        val q = normalize(query)
        if (q.isBlank()) return emptyList()

        val directDioceses = allDioceses
            .filter {
                normalize(it.name).contains(q) ||
                    q.contains(normalize(it.name)) ||
                    normalize(it.name.removeSuffix("교구").removeSuffix("대교구")).contains(q)
            }
            .map { SearchResult(it.name, DioceseMatch.Found(it), "교구명 검색") }
        if (directDioceses.isNotEmpty()) return directDioceses

        fun result(label: String, diocese: Diocese) = SearchResult(label, DioceseMatch.Found(diocese), "지역 검색")
        fun ambiguous(label: String, dioceses: List<Diocese>, reason: String) =
            SearchResult(label, DioceseMatch.Ambiguous(dioceses, reason), "경계 지역")

        val results = mutableListOf<SearchResult>()

        val keywordGroups = listOf(
            Triple(seoul, listOf("서울"), "서울특별시"),
            Triple(incheon, listOf("인천", "김포", "부천"), "인천·김포·부천"),
            Triple(uijeongbu, listOf("고양", "구리", "남양주", "동두천", "양주", "의정부", "파주", "연천"), "경기 북부"),
            Triple(suwon, listOf("과천", "광명", "광주", "군포", "성남", "수원", "안성", "안양", "여주", "오산", "용인", "의왕", "이천", "평택", "하남", "화성", "양평"), "경기 남부"),
            Triple(daejeon, listOf("대전", "충남", "충청남", "세종", "공주", "논산", "계룡", "금산", "당진", "보령", "서산", "아산", "천안", "홍성", "예산", "태안", "부여", "서천", "청양"), "대전·충남·세종 대부분"),
            Triple(cheongju, listOf("청주", "충북", "충청북", "충주", "음성", "진천", "괴산", "증평", "보은", "옥천", "영동", "부강면"), "충북 대부분·세종 부강면"),
            Triple(wonju, listOf("원주", "삼척", "태백", "영월", "정선", "횡성", "제천", "단양"), "강원 남부·충북 북동부"),
            Triple(chuncheon, listOf("춘천", "강릉", "속초", "고성", "양구", "양양", "인제", "철원", "화천", "홍천"), "강원 북부·동부 일부"),
            Triple(daegu, listOf("대구", "포항", "경주", "김천", "구미", "경산", "영천", "칠곡", "성주", "고령", "청도"), "대구·경북 남부"),
            Triple(andong, listOf("안동", "문경", "상주", "영주", "봉화", "영덕", "영양", "예천", "울진", "의성", "청송"), "경북 북부"),
            Triple(busan, listOf("부산", "울산", "양산"), "부산·울산·양산"),
            Triple(masan, listOf("창원", "마산", "진주", "통영", "거제", "사천", "김해진영", "진영읍", "진례면", "한림면", "밀양하남읍", "하남읍", "초동면"), "경남 대부분"),
            Triple(gwangju, listOf("광주", "전남", "전라남", "목포", "여수", "순천", "나주", "광양", "해남"), "광주·전남"),
            Triple(jeonju, listOf("전주", "전북", "전라북", "익산", "군산", "정읍", "남원", "김제", "완주", "무주", "진안", "장수", "임실", "순창", "고창", "부안"), "전북"),
            Triple(jeju, listOf("제주", "서귀포"), "제주특별자치도")
        )

        keywordGroups.forEach { (diocese, keywords, label) ->
            if (keywords.any { normalize(it).contains(q) || q.contains(normalize(it)) }) {
                results += result(label, diocese)
            }
        }

        if (listOf("시흥", "안산").any { normalize(it).contains(q) || q.contains(normalize(it)) }) {
            results += ambiguous(
                "시흥·안산 일부",
                listOf(incheon, suwon),
                "시흥·안산은 인천교구와 수원교구 경계가 나뉘므로 상세 주소 또는 GPS 확인이 필요합니다."
            )
        }
        if (listOf("평창", "동해").any { normalize(it).contains(q) || q.contains(normalize(it)) }) {
            results += ambiguous(
                "평창·동해 일부",
                listOf(chuncheon, wonju),
                "평창·동해는 춘천교구와 원주교구 경계가 나뉘므로 상세 주소 또는 GPS 확인이 필요합니다."
            )
        }
        if (q.contains("김해") && !q.contains("진영") && !q.contains("진례") && !q.contains("한림") && !q.contains("생림")) {
            results += ambiguous(
                "김해시",
                listOf(busan, masan),
                "김해시는 부산교구와 마산교구가 나뉩니다. 진영읍·진례면·한림면 및 생림면 대부분은 마산교구입니다."
            )
        }
        if (q.contains("밀양") && !q.contains("하남읍") && !q.contains("초동면")) {
            results += ambiguous(
                "밀양시",
                listOf(busan, masan),
                "밀양시는 부산교구와 마산교구가 나뉩니다. 하남읍·초동면은 마산교구입니다."
            )
        }

        return results.distinctBy { it.label + it.match.toString() }
    }

    fun updateOrdinary(
        dioceseName: String,
        ordinary: String,
        title: String,
        statusLabel: String,
        rememberOrdinary: Boolean
    ): Boolean {
        val diocese = allDioceses.firstOrNull { it.name == dioceseName } ?: return false
        diocese.ordinary = ordinary
        diocese.title = title
        diocese.statusLabel = statusLabel
        diocese.rememberOrdinary = rememberOrdinary
        return true
    }

    private fun normalize(value: String?): String = value.orEmpty().replace(" ", "")
}

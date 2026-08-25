package kr.catholic.dioceselocator

import android.Manifest
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { DioceseApp() } }
    }

    @Composable
    private fun DioceseApp() {
        var selectedTab by remember { mutableIntStateOf(0) }
        var dataRevision by remember { mutableIntStateOf(0) }
        var boundaryUpdateVersion by remember { mutableStateOf<Int?>(null) }

        LaunchedEffect(Unit) {
            RemoteDataManager.applyCached(this@MainActivity)?.let {
                if (it.appliedOrdinaries > 0) dataRevision++
                if (it.boundaryUpdateRequired) boundaryUpdateVersion = it.remoteBoundaryVersion
            }
            RemoteDataManager.refreshAsync(this@MainActivity) { result ->
                if (result.appliedOrdinaries > 0) dataRevision++
                if (result.boundaryUpdateRequired) boundaryUpdateVersion = result.remoteBoundaryVersion
            }
        }

        // 원격 교구장 데이터가 적용되면 하위 UI를 다시 그리기 위한 상태 참조
        @Suppress("UNUSED_VARIABLE") val currentDataRevision = dataRevision

        boundaryUpdateVersion?.let { remoteVersion ->
            AlertDialog(
                onDismissRequest = { boundaryUpdateVersion = null },
                title = { Text("교구 관할 정보 업데이트 필요") },
                text = {
                    Text(
                        "교구 관할 구역이 변경되었습니다. 정확한 현재 위치 판정을 위해 앱을 최신 버전으로 업데이트해 주세요. " +
                            "현재 앱 경계 데이터: ${RemoteDataManager.CURRENT_BOUNDARY_VERSION}, 최신 데이터: $remoteVersion"
                    )
                },
                confirmButton = {
                    TextButton(onClick = { boundaryUpdateVersion = null }) { Text("확인") }
                }
            )
        }

        Scaffold(
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        icon = { Text("◎") },
                        label = { Text("현재 위치") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        icon = { Text("⌕") },
                        label = { Text("미리 검색") }
                    )
                }
            }
        ) { innerPadding ->
            when (selectedTab) {
                0 -> LocationTab(Modifier.padding(innerPadding))
                else -> SearchTab(Modifier.padding(innerPadding))
            }
        }
    }

    @Composable
    private fun LocationTab(modifier: Modifier = Modifier) {
        var state by remember { mutableStateOf<UiState>(UiState.Idle) }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) locate { state = it } else state = UiState.Error("위치 권한이 필요합니다.")
        }

        fun requestLocation() {
            val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (fine) locate { state = it } else launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("성 크리스토퍼", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("현재 위치의 천주교 관할 교구와 교구장을 확인합니다.")

            Button(onClick = ::requestLocation, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("현재 위치 확인")
            }

            when (val s = state) {
                UiState.Idle -> Text("캠프·피정지에서 버튼을 눌러 확인하세요.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                UiState.Loading -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                is UiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error)
                is UiState.Result -> ResultCard(s.match, "현재 위치", s.region.fullAddress.ifBlank { "주소 확인 불가" })
                is UiState.OutOfService -> OutOfServiceCard(s.address)
            }
        }
    }

    @Composable
    private fun SearchTab(modifier: Modifier = Modifier) {
        var query by remember { mutableStateOf("") }
        var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
        var searched by remember { mutableStateOf(false) }

        fun runSearch() {
            searched = true
            results = DioceseRepository.search(query)
        }

        Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("미리 검색", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("캠프·피정 장소의 지역명이나 교구명을 미리 확인할 수 있습니다.")

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("지역 또는 교구") },
                placeholder = { Text("예: 여주, 평창, 수원교구") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch() })
            )
            Button(
                onClick = { runSearch() },
                enabled = query.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("검색")
            }

            if (!searched) {
                Text("지역명은 시·군·구 또는 읍·면까지 입력하면 더 정확합니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (results.isEmpty()) {
                Text("검색 결과가 없습니다. 더 넓은 지역명이나 교구명을 입력해 보세요.", color = MaterialTheme.colorScheme.error)
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(results) { result ->
                        ResultCard(result.match, result.label, result.description)
                    }
                }
            }
        }
    }

    @Composable
    private fun OutOfServiceCard(address: String) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("서비스 지역이 아닙니다", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("현재 이 앱은 대한민국 내 천주교 교구만 지원합니다.")
                if (address.isNotBlank()) {
                    Text(address, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    @Composable
    private fun ResultCard(match: DioceseMatch, heading: String, subheading: String = "") {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(heading, style = MaterialTheme.typography.labelLarge)
                if (subheading.isNotBlank()) Text(subheading, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider()
                when (match) {
                    is DioceseMatch.Found -> OrdinaryBlock(match.diocese)
                    is DioceseMatch.Ambiguous -> {
                        Text("세부 경계 확인 필요", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(match.candidates.joinToString(" / ") { it.name })
                        match.candidates.forEach { OrdinaryBlock(it, compact = true) }
                        Text(match.reason, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is DioceseMatch.Unknown -> {
                        Text("교구 판정 불가", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(match.reason)
                    }
                }
            }
        }
        Text(
            "교구장 자료 기준: 한국천주교주교회의 온라인 주소록 · 앱 실행 시 자동 확인",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    @Composable
    private fun OrdinaryBlock(diocese: Diocese, compact: Boolean = false) {
        Text(
            diocese.name,
            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text("${diocese.statusLabel}  ${diocese.ordinary} ${diocese.title}", style = MaterialTheme.typography.titleMedium)
        if (!diocese.rememberOrdinary) {
            Text(
                "(교구장을 기억하지 않음)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
        }
    }

    private fun locate(onState: (UiState) -> Unit) {
        onState(UiState.Loading)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            onState(UiState.Error("위치 권한이 필요합니다.")); return
        }
        val client = LocationServices.getFusedLocationProviderClient(this)
        client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location == null) onState(UiState.Error("현재 위치를 가져오지 못했습니다. 위치 서비스를 켜고 다시 시도하세요."))
                else reverseGeocode(location.latitude, location.longitude, onState)
            }
            .addOnFailureListener { onState(UiState.Error("위치 확인 실패: ${it.localizedMessage ?: "알 수 없는 오류"}")) }
    }

    private fun reverseGeocode(lat: Double, lon: Double, onState: (UiState) -> Unit) {
        if (!Geocoder.isPresent()) { onState(UiState.Error("이 기기에서 주소 변환 서비스를 사용할 수 없습니다.")); return }
        val geocoder = Geocoder(this, Locale.KOREAN)
        fun emit(address: Address?) {
            if (address == null) { onState(UiState.Error("현재 좌표의 주소를 확인하지 못했습니다.")); return }
            val countryCode = address.countryCode?.uppercase(Locale.ROOT)
            if (countryCode != "KR") {
                onState(UiState.OutOfService(address.getAddressLine(0).orEmpty()))
                return
            }
            val region = Region(address.adminArea, address.locality, address.subLocality, address.featureName, address.getAddressLine(0).orEmpty())
            onState(UiState.Result(region, DioceseRepository.match(region)))
        }
        if (Build.VERSION.SDK_INT >= 33) {
            geocoder.getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) = emit(addresses.firstOrNull())
                override fun onError(errorMessage: String?) = onState(UiState.Error("주소 확인 실패: ${errorMessage ?: "알 수 없는 오류"}"))
            })
        } else {
            Thread {
                try { emit(geocoder.getFromLocation(lat, lon, 1)?.firstOrNull()) }
                catch (e: Exception) { onState(UiState.Error("주소 확인 실패: ${e.localizedMessage ?: "알 수 없는 오류"}")) }
            }.start()
        }
    }
}

sealed interface UiState {
    data object Idle : UiState
    data object Loading : UiState
    data class Error(val message: String) : UiState
    data class OutOfService(val address: String = "") : UiState
    data class Result(val region: Region, val match: DioceseMatch) : UiState
}

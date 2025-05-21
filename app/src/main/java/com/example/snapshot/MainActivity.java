package com.example.snapshot;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.NavigationUI;
import androidx.navigation.fragment.NavHostFragment;

import com.example.snapshot.databinding.ActivityMainBinding;
import com.example.snapshot.repository.UserRepository;
import com.example.snapshot.service.TagSuggestionService;
import com.example.snapshot.ui.auth.LoginActivity;
import com.example.snapshot.ui.post.CreatePostActivity;
import com.example.snapshot.ui.test.TagSaveTestActivity;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.firebase.auth.FirebaseUser;

public class MainActivity extends AppCompatActivity implements NavigationBarView.OnItemSelectedListener {

    private static final String TAG = "MainActivity";
    private static final int PLAY_SERVICES_RESOLUTION_REQUEST = 9000;

    private ActivityMainBinding binding;
    private NavController navController;
    private UserRepository userRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // 하드웨어 가속 관련 로그 출력
        Log.d(TAG, "애플리케이션 초기화 (하드웨어 가속은 매니페스트에서 설정됨)");
        
        // 뷰 바인딩 초기화
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        
        // Google Play 서비스 확인
        checkGooglePlayServices();
        
        // 저장소 초기화
        userRepository = UserRepository.getInstance();
        
        // 로그인 확인
        FirebaseUser currentUser = userRepository.getCurrentUser();
        if (currentUser == null) {
            // 로그인되지 않은 경우 로그인 화면으로 이동
            navigateToLoginActivity();
            return;
        }
        
        // 내비게이션 설정
        setupNavigation();
        
        // 플로팅 액션 버튼 클릭 이벤트
        binding.fabAddPost.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, CreatePostActivity.class);
            startActivity(intent);
        });
    }
    
    /**
     * Google Play 서비스 가용성 확인
     */
    private void checkGooglePlayServices() {
        GoogleApiAvailability apiAvailability = GoogleApiAvailability.getInstance();
        int resultCode = apiAvailability.isGooglePlayServicesAvailable(this);
        
        if (resultCode != ConnectionResult.SUCCESS) {
            if (apiAvailability.isUserResolvableError(resultCode)) {
                apiAvailability.getErrorDialog(this, resultCode, PLAY_SERVICES_RESOLUTION_REQUEST)
                        .show();
            } else {
                Log.e(TAG, "이 기기는 Google Play 서비스를 지원하지 않습니다.");
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // 리소스 해제
        try {
            // 바인딩 해제
            if (binding != null) {
                binding = null;
            }
            
            // TagSuggestionService 인스턴스 해제
            TagSuggestionService.releaseInstance();
            
        } catch (Exception e) {
            Log.e(TAG, "리소스 해제 중 오류 발생", e);
        }
    }
    
    private void setupNavigation() {
        try {
            // NavHostFragment 직접 찾기
            NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host_fragment);
                
            if (navHostFragment != null) {
                navController = navHostFragment.getNavController();
                
                // 바텀 네비게이션 설정
                binding.bottomNavigation.setOnItemSelectedListener(this);
                
                // 더미 메뉴 아이템 (FAB 중앙 위치용) 비활성화
                binding.bottomNavigation.getMenu().findItem(R.id.navigation_dummy).setEnabled(false);
                
                // Navigation UI 설정 - 바텀 네비게이션과 네비게이션 컨트롤러 연결
                NavigationUI.setupWithNavController(binding.bottomNavigation, navController);
            } else {
                android.util.Log.e("MainActivity", "NavHostFragment를 찾을 수 없습니다.");
            }
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "Navigation 설정 중 오류 발생: " + e.getMessage());
        }
    }
    
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        try {
            int itemId = item.getItemId();
            int currentDestinationId = navController.getCurrentDestination() != null ? navController.getCurrentDestination().getId() : 0;
            
            if (itemId == currentDestinationId) {
                return false;
            }
            
            Bundle args = new Bundle();
            args.putInt("previousDestinationId", currentDestinationId);

            if (itemId == R.id.navigation_home) {
                navController.navigate(R.id.navigation_home, args);
                return true;
            } else if (itemId == R.id.navigation_search) {
                navController.navigate(R.id.navigation_search, args);
                return true;
            } else if (itemId == R.id.navigation_notifications) {
                navController.navigate(R.id.navigation_notifications, args);
                return true;
            } else if (itemId == R.id.navigation_profile) {
                navController.navigate(R.id.navigation_profile, args);
                return true;
            }
            
            return false;
        } catch (Exception e) {
            android.util.Log.e("MainActivity", "네비게이션 에러: " + e.getMessage());
            return false;
        }
    }
    
    private void navigateToLoginActivity() {
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        // 백 스택 비우기
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        // 태그 저장 테스트 메뉴 항목 추가
        menu.add(Menu.NONE, 1001, Menu.NONE, "태그 저장 테스트");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1001) {
            // 태그 저장 테스트 액티비티 시작
            Intent intent = new Intent(this, TagSaveTestActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
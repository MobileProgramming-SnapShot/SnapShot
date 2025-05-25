package com.example.snapshot.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;

import com.bumptech.glide.Glide;
import com.example.snapshot.R;
import com.example.snapshot.databinding.ActivityProfileBinding;
import com.example.snapshot.model.Post;
import com.example.snapshot.model.User;
import com.example.snapshot.repository.PostRepository;
import com.example.snapshot.repository.ReportRepository;
import com.example.snapshot.repository.UserRepository;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

public class ProfileActivity extends AppCompatActivity {

    private ActivityProfileBinding binding;
    private UserRepository userRepository;
    private PostRepository postRepository;

    private String userId;
    private User profileUser;
    private ProfilePostAdapter adapter;
    private List<Post> postList = new ArrayList<>();

    // 프로필 편집 결과를 처리하기 위한 ActivityResultLauncher
    private ActivityResultLauncher<Intent> editProfileLauncher;

    public static final String EXTRA_USER_ID = "extra_user_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // ActivityResultLauncher 초기화
        editProfileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == AppCompatActivity.RESULT_OK) {
                        // 프로필이 성공적으로 편집되었으면 사용자 데이터 새로고침
                        loadUserProfile(); // 프로필 정보 및 UI 업데이트
                    }
                }
        );

        // 저장소 초기화
        userRepository = UserRepository.getInstance();
        postRepository = PostRepository.getInstance();

        // 툴바 설정
        setupToolbar();

        // 인텐트에서 사용자 ID 가져오기
        userId = getIntent().getStringExtra(EXTRA_USER_ID);

        // 사용자 ID가 없는 경우 현재 로그인한 사용자의 프로필 표시
        if (userId == null || userId.isEmpty()) {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null) {
                userId = currentUser.getUid();
            } else {
                Toast.makeText(this, "사용자 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }

        // 리사이클러뷰 설정
        setupRecyclerView();

        // 사용자 정보 로드
        loadUserProfile();

        // 포스트 목록 로드
        loadUserPosts();

        // 이벤트 리스너 설정
        setupListeners();
    }

    private void setupToolbar() {
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 현재 로그인한 사용자가 자신의 프로필을 보는 것이 아닌 경우 신고 메뉴 표시
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && !currentUser.getUid().equals(userId)) {
            getMenuInflater().inflate(R.menu.menu_profile, menu);
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_report_user) {
            showReportDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupRecyclerView() {
        adapter = new ProfilePostAdapter(this, postList);
        binding.recyclerPosts.setLayoutManager(new GridLayoutManager(this, 3));
        binding.recyclerPosts.setAdapter(adapter);
    }

    private void loadUserProfile() {
        showLoading(true);

        userRepository.getUserById(userId)
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        profileUser = documentSnapshot.toObject(User.class);
                        if (profileUser != null) {
                            updateProfileUI();
                        }
                    } else {
                        Toast.makeText(this, "사용자 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "사용자 정보를 로드하는 중 오류가 발생했습니다: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void updateProfileUI() {
        // 사용자 이름 설정
        binding.tvUsername.setText(profileUser.getUsername());

        // 프로필 이미지 설정
        if (profileUser.getProfilePicUrl() != null && !profileUser.getProfilePicUrl().isEmpty()) {
            Glide.with(this)
                    .load(profileUser.getProfilePicUrl())
                    .placeholder(R.drawable.default_profile)
                    .error(R.drawable.default_profile)
                    .into(binding.ivProfilePic);
        }

        // 관심사 설정 (새로운 EditText 사용)
        // bio 필드를 관심사로 사용하거나, User 모델에 새로운 필드를 추가해야 합니다.
        // 현재 User 모델에 'interests' 필드가 없다고 가정하고 bio를 사용합니다.
        // 만약 User 모델에 'interests' 필드를 추가했다면 `profileUser.getInterests()`를 사용하세요.
        if (profileUser.getBio() != null && !profileUser.getBio().isEmpty()) {
            binding.etInterests.setText(profileUser.getBio());
            // EditText는 기본적으로 visible하지만, bio가 없으면 힌트만 보여주기 위함
            binding.etInterests.setVisibility(View.VISIBLE);
        } else {
            binding.etInterests.setText(""); // 기존 bio가 없으면 비워둡니다.
            binding.etInterests.setVisibility(View.VISIBLE); // 힌트가 보이도록 유지
        }

        // 게시물/팔로워/팔로잉 버튼 텍스트 설정
        // 이 예시에서는 User 모델에 직접 posts, followers, following count가 있다고 가정합니다.
        // 실제 데이터는 Firebase에서 해당 컬렉션을 쿼리하여 가져와야 합니다.
        // 임시로 0으로 설정합니다. loadUserPosts에서 게시물 수는 업데이트됩니다.
        binding.btnPosts.setText(getString(R.string.posts_count, 0)); // 게시물 수는 loadUserPosts에서 업데이트될 것
        binding.btnFollowers.setText(getString(R.string.followers_count, profileUser.getFollowerCount()));
        binding.btnFollowing.setText(getString(R.string.following_count, profileUser.getFollowingCount()));


        // 현재 사용자가 자신의 프로필을 보는 경우 EditText를 편집 가능하게 하고, 다른 사용자의 경우 읽기 전용으로 설정
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && currentUser.getUid().equals(userId)) {
            binding.etInterests.setFocusableInTouchMode(true);
            binding.etInterests.setHint(R.string.hint_interests_editable);
            // '편집' 기능을 위한 UI가 없으므로 임의로 et_interests를 편집 가능하게 함
            // 실제 앱에서는 별도의 편집 버튼이나 EditProfileActivity에서 처리해야 함
        } else {
            binding.etInterests.setFocusable(false);
            binding.etInterests.setHint(R.string.hint_interests_readonly);
            // 다른 사용자의 프로필인 경우 팔로우 버튼을 표시합니다.
            // XML에 팔로우 버튼이 직접 정의되어 있지 않으므로, 이 로직은 주석 처리합니다.
            // 만약 팔로우 버튼을 추가한다면 다시 활성화해야 합니다.
            // checkFollowStatus();
        }
    }

    private void checkFollowStatus() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            // binding.btnFollow.setVisibility(View.GONE); // btnFollow가 없으므로 주석 처리
            return;
        }

        userRepository.getUserById(currentUser.getUid())
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null && user.getFollowing() != null) {
                            boolean isFollowing = user.getFollowing().contains(userId);
                            // updateFollowButton(isFollowing); // btnFollow가 없으므로 주석 처리
                        }
                    }
                });
    }

    private void updateFollowButton(boolean isFollowing) {
        // XML에 팔로우 버튼이 없으므로 이 메서드는 현재 사용되지 않습니다.
        // 만약 팔로우 버튼을 추가한다면 여기에 로직을 구현하세요.
        // if (isFollowing) {
        //     binding.btnFollow.setText(R.string.unfollow);
        //     binding.btnFollow.setBackgroundResource(R.drawable.bg_button_secondary);
        // } else {
        //     binding.btnFollow.setText(R.string.follow);
        //     binding.btnFollow.setBackgroundResource(R.drawable.bg_button_primary);
        // }
    }

    private void loadUserPosts() {
        Query query = postRepository.getPostsByUser(userId);

        query.addSnapshotListener((queryDocumentSnapshots, firebaseFirestoreException) -> {
            if (firebaseFirestoreException != null) {
                showLoading(false);
                Toast.makeText(this, "포스트를 로드하는 중 오류가 발생했습니다: " + firebaseFirestoreException.getMessage(),
                        Toast.LENGTH_SHORT).show();
                return;
            }

            if (queryDocumentSnapshots == null || queryDocumentSnapshots.isEmpty()) { // Fixed typo here!
                postList.clear();
                adapter.notifyDataSetChanged();
                binding.btnPosts.setText(getString(R.string.posts_count, 0)); // 게시물 수를 0으로 업데이트
                // binding.tvEmptyPosts.setVisibility(View.VISIBLE); // tvEmptyPosts가 없으므로 주석 처리
                showLoading(false);
                return;
            }

            postList.clear();

            for (DocumentSnapshot document : queryDocumentSnapshots.getDocuments()) {
                Post post = document.toObject(Post.class);
                if (post != null) {
                    postList.add(post);
                }
            }

            adapter.notifyDataSetChanged();

            // 포스트 수 업데이트
            binding.btnPosts.setText(getString(R.string.posts_count, postList.size()));

            // 빈 상태 표시 (현재 XML에 해당 뷰가 없으므로 주석 처리)
            // if (postList.isEmpty()) {
            //     binding.tvEmptyPosts.setVisibility(View.VISIBLE);
            // } else {
            //     binding.tvEmptyPosts.setVisibility(View.GONE);
            // }

            showLoading(false);
        });
    }

    private void setupListeners() {
        // 게시물 버튼 클릭 (임시)
        binding.btnPosts.setOnClickListener(v -> {
            // 여기에 게시물 버튼 클릭 시 수행할 작업을 추가합니다.
            // 예를 들어, 사용자 자신의 게시물을 보여주는 탭으로 이동하거나 필터링할 수 있습니다.
            Toast.makeText(this, "게시물 버튼 클릭됨", Toast.LENGTH_SHORT).show();
        });

        // 팔로워 버튼 클릭
        binding.btnFollowers.setOnClickListener(v -> {
            // 팔로워 목록 화면으로 이동
            Intent intent = new Intent(this, FollowListActivity.class);
            intent.putExtra(FollowListActivity.EXTRA_USER_ID, userId);
            intent.putExtra(FollowListActivity.EXTRA_LIST_TYPE, FollowListActivity.TYPE_FOLLOWERS);
            startActivity(intent);
        });

        // 팔로잉 버튼 클릭
        binding.btnFollowing.setOnClickListener(v -> {
            // 팔로잉 목록 화면으로 이동
            Intent intent = new Intent(this, FollowListActivity.class);
            intent.putExtra(FollowListActivity.EXTRA_USER_ID, userId);
            intent.putExtra(FollowListActivity.EXTRA_LIST_TYPE, FollowListActivity.TYPE_FOLLOWING);
            startActivity(intent);
        });

        // 현재 사용자가 자신의 프로필을 볼 때만 et_interests를 편집 가능하게 합니다.
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && currentUser.getUid().equals(userId)) {
            // et_interests에 직접적인 클릭 리스너를 추가하는 대신,
            // 포커스를 얻을 때만 편집 가능하도록 설정하는 것이 좋습니다.
            // 또는 별도의 "편집" 버튼을 만들어 EditProfileActivity로 이동하게 할 수 있습니다.
            // 현재 XML에 명시적인 "프로필 편집" 버튼이 없으므로, et_interests에 대한 수정은 EditProfileActivity에서 이루어지는 것이 더 적합합니다.
            // 이 곳에서는 사용자의 프로필 편집 기능을 제공하지 않습니다.

            // 관심사 입력 칸 포커스 변경 리스너 (선택 사항)
            binding.etInterests.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    // 포커스를 잃었을 때 변경된 관심사를 저장하는 로직을 여기에 추가할 수 있습니다.
                    // 예를 들어: saveInterests(binding.etInterests.getText().toString());
                    Toast.makeText(this, "관심사 저장 (현재는 임시)", Toast.LENGTH_SHORT).show();
                }
            });
        }
        // else {
        //     // 다른 사용자의 프로필을 볼 때 팔로우 버튼을 토글하는 로직 (btnFollow가 없으므로 주석 처리)
        //     // binding.btnFollow.setOnClickListener(v -> toggleFollow());
        // }
    }

    private void toggleFollow() {
        // 이 메서드는 현재 XML에 팔로우 버튼이 없으므로 사용되지 않습니다.
        // 만약 팔로우 버튼을 추가한다면 여기에 로직을 구현하세요.
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        String currentUserId = currentUser.getUid();

        // 자기 자신을 팔로우할 수 없음
        if (currentUserId.equals(userId)) {
            return;
        }

        showLoading(true);

        userRepository.getUserById(currentUserId)
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null) {
                            boolean isFollowing = user.getFollowing() != null && user.getFollowing().contains(userId);

                            if (isFollowing) {
                                // 언팔로우
                                userRepository.unfollowUser(currentUserId, userId)
                                        .addOnSuccessListener(aVoid -> {
                                            // updateFollowButton(false); // btnFollow가 없으므로 주석 처리
                                            // 팔로워 수 감소
                                            if (profileUser != null) {
                                                profileUser.setFollowerCount(Math.max(0, profileUser.getFollowerCount() - 1));
                                                binding.btnFollowers.setText(getString(R.string.followers_count, profileUser.getFollowerCount()));
                                            }
                                            showLoading(false);
                                        })
                                        .addOnFailureListener(e -> {
                                            showLoading(false);
                                            Toast.makeText(this, "언팔로우 중 오류가 발생했습니다: " + e.getMessage(),
                                                    Toast.LENGTH_SHORT).show();
                                        });
                            } else {
                                // 팔로우
                                userRepository.followUser(currentUserId, userId)
                                        .addOnSuccessListener(aVoid -> {
                                            // updateFollowButton(true); // btnFollow가 없으므로 주석 처리
                                            // 팔로워 수 증가
                                            if (profileUser != null) {
                                                profileUser.setFollowerCount(profileUser.getFollowerCount() + 1);
                                                binding.btnFollowers.setText(getString(R.string.followers_count, profileUser.getFollowerCount()));
                                            }
                                            showLoading(false);
                                        })
                                        .addOnFailureListener(e -> {
                                            showLoading(false);
                                            Toast.makeText(this, "팔로우 중 오류가 발생했습니다: " + e.getMessage(),
                                                    Toast.LENGTH_SHORT).show();
                                        });
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "사용자 정보를 로드하는 중 오류가 발생했습니다: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void showLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }

    /**
     * 사용자 신고 다이얼로그 표시
     */
    private void showReportDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_report, null);
        builder.setView(dialogView);

        // 다이얼로그 제목 설정
        TextView titleText = dialogView.findViewById(R.id.title_report);
        titleText.setText(R.string.report_user_title);

        // 라디오 그룹 및 기타 이유 입력창 설정
        RadioGroup radioGroup = dialogView.findViewById(R.id.radio_group_report_reason);
        EditText customReasonEdit = dialogView.findViewById(R.id.edit_text_custom_reason);

        // '기타' 옵션 선택 시 입력창 표시
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_other) {
                customReasonEdit.setVisibility(View.VISIBLE);
            } else {
                customReasonEdit.setVisibility(View.GONE);
            }
        });

        // 신고 제출 및 취소 버튼 추가
        builder.setPositiveButton(R.string.report_submit, (dialogInterface, i) -> {
            // 선택된 라디오 버튼 가져오기
            int selectedId = radioGroup.getCheckedRadioButtonId();

            if (selectedId == -1) {
                // 아무것도 선택되지 않은 경우
                Toast.makeText(this, R.string.report_reason, Toast.LENGTH_SHORT).show();
                return;
            }

            String reason;
            if (selectedId == R.id.radio_other) {
                reason = customReasonEdit.getText().toString().trim();
                if (reason.isEmpty()) {
                    customReasonEdit.setError(getString(R.string.error_empty_fields));
                    return;
                }
            } else {
                RadioButton selectedRadioButton = dialogView.findViewById(selectedId);
                reason = selectedRadioButton.getText().toString();
            }

            submitReport(reason);
        });

        builder.setNegativeButton(R.string.cancel, null);

        // 다이얼로그 표시
        builder.create().show();
    }

    /**
     * 사용자 신고 제출
     * @param reason 신고 사유
     */
    private void submitReport(String reason) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, R.string.login_required, Toast.LENGTH_SHORT).show();
            return;
        }

        if (profileUser == null) {
            Toast.makeText(this, R.string.error_loading_post, Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);
        String reporterId = currentUser.getUid();

        // 신고 제출
        ReportRepository reportRepository = ReportRepository.getInstance();
        reportRepository.reportUser(reporterId, userId, reason)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    Toast.makeText(this, R.string.report_success, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    // 이미 신고한 경우 특별 메시지 표시
                    if (e.getMessage() != null && e.getMessage().contains("이미 이 사용자를 신고하셨습니다")) {
                        Toast.makeText(this, R.string.report_already_reported, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, getString(R.string.report_failed) + ": " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
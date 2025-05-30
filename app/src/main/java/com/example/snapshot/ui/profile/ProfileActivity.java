package com.example.snapshot.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.example.snapshot.ui.auth.LoginActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;

import androidx.core.content.ContextCompat;
import android.content.res.ColorStateList;

public class ProfileActivity extends AppCompatActivity {

    private ActivityProfileBinding binding;
    private UserRepository userRepository;
    private PostRepository postRepository;
    private ReportRepository reportRepository;

    private String userId;
    private User profileUser;
    private ProfilePostAdapter adapter;
    private List<Post> postList = new ArrayList<>();

    private ActivityResultLauncher<Intent> editProfileLauncher;

    public static final String EXTRA_USER_ID = "extra_user_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        editProfileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK) {
                        loadUserProfile();
                    }
                }
        );

        userRepository = UserRepository.getInstance();
        postRepository = PostRepository.getInstance();
        reportRepository = ReportRepository.getInstance();

        String intentUserId = getIntent().getStringExtra(EXTRA_USER_ID);
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (intentUserId == null || intentUserId.isEmpty()) {
            if (currentUser != null) {
                userId = currentUser.getUid();
            } else {
                Toast.makeText(this, "사용자 정보를 찾을 수 없습니다. 로그인 화면으로 이동합니다.", Toast.LENGTH_LONG).show();
                navigateToLogin();
                return;
            }
        } else {
            userId = intentUserId;
        }

        setupToolbar();

        setupRecyclerView();

        loadUserProfile();

        loadUserPosts();

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
        getMenuInflater().inflate(R.menu.profile_menu, menu);
        MenuItem actionSettingsItem = menu.findItem(R.id.action_settings);

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        boolean isMyProfile = currentUser != null && userId.equals(currentUser.getUid());

        if (actionSettingsItem != null) {
            if (isMyProfile) {
                actionSettingsItem.setIcon(R.drawable.ic_settings);
                if (getSupportActionBar() != null) getSupportActionBar().setTitle(getString(R.string.title_my_profile));
            } else {
                actionSettingsItem.setIcon(R.drawable.ic_report);
                if (getSupportActionBar() != null) getSupportActionBar().setTitle(getString(R.string.title_user_profile));
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            boolean isMyProfile = currentUser != null && userId.equals(currentUser.getUid());
            if (isMyProfile) {
                showSettingsOptions();
            } else {
                if (profileUser != null) {
                    showReportUserDialog();
                } else {
                    Toast.makeText(this, "사용자 정보를 불러오는 중입니다. 잠시 후 다시 시도해주세요.", Toast.LENGTH_SHORT).show();
                }
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showSettingsOptions() {
        new AlertDialog.Builder(this)
                .setTitle("설정")
                .setItems(new CharSequence[]{"로그아웃"}, (dialog, which) -> {
                    if (which == 0) {
                        FirebaseAuth.getInstance().signOut();
                        navigateToLogin();
                    }
                })
                .setNegativeButton("취소", null)
                .show();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showReportUserDialog() {
        if (profileUser == null) {
            Toast.makeText(this, "신고할 사용자 정보가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        final String[] reportReasons = {
                getString(R.string.report_reason_inappropriate),
                getString(R.string.report_reason_spam),
                getString(R.string.report_reason_harassment),
                getString(R.string.report_reason_impersonation),
                getString(R.string.report_reason_other)
        };

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.report_user_title))
                .setItems(reportReasons, (dialog, which) -> {
                    String selectedReason = reportReasons[which];
                    if (selectedReason.equals(getString(R.string.report_reason_other))) {
                        showCustomReportReasonDialog();
                    } else {
                        submitReport(selectedReason, null);
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void showCustomReportReasonDialog() {
        final EditText input = new EditText(this);
        input.setHint(getString(R.string.report_custom_reason_hint));

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.report_reason_other))
                .setView(input)
                .setPositiveButton(getString(R.string.report_submit), (dialog, which) -> {
                    String customReason = input.getText().toString().trim();
                    if (!customReason.isEmpty()) {
                        submitReport(getString(R.string.report_reason_other), customReason);
                    } else {
                        Toast.makeText(this, "신고 사유를 입력해주세요.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void submitReport(String reason, @Nullable String customDetail) {
        FirebaseUser reporterUser = FirebaseAuth.getInstance().getCurrentUser();
        if (reporterUser == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (profileUser == null) {
            Toast.makeText(this, "신고 대상 사용자 정보가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        String reporterId = reporterUser.getUid();
        String reportedUserId = profileUser.getUserId();
        String finalReason = customDetail != null ? customDetail : reason;

        reportRepository.reportUser(reporterId, reportedUserId, finalReason)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, getString(R.string.report_success), Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    String errorMessage = getString(R.string.report_failed);
                    if (e.getMessage() != null && e.getMessage().contains("이미 이 사용자를 신고하셨습니다.")) {
                        errorMessage = getString(R.string.report_already_reported);
                    } else if (e.getMessage() != null) {
                        errorMessage += ": " + e.getMessage();
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
                    Log.e("ProfileActivity", "Error reporting user: " + e.getMessage());
                });
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
                    showLoading(false);
                    if (documentSnapshot.exists()) {
                        profileUser = documentSnapshot.toObject(User.class);
                        if (profileUser != null) {
                            updateProfileUI();
                            invalidateOptionsMenu();
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
        binding.tvUsername.setText(profileUser.getUsername());

        if (profileUser.getProfilePicUrl() != null && !profileUser.getProfilePicUrl().isEmpty()) {
            Glide.with(this)
                    .load(profileUser.getProfilePicUrl())
                    .placeholder(R.drawable.default_profile)
                    .error(R.drawable.default_profile)
                    .into(binding.ivProfilePic);
        } else {
            binding.ivProfilePic.setImageResource(R.drawable.default_profile);
        }

        if (profileUser.getBio() != null && !profileUser.getBio().isEmpty()) {
            binding.tvBio.setText(profileUser.getBio());
            binding.tvBio.setVisibility(View.VISIBLE);
        } else {
            binding.tvBio.setVisibility(View.GONE);
        }

        binding.tvFollowersCount.setText(String.valueOf(profileUser.getFollowerCount()));
        binding.tvFollowingCount.setText(String.valueOf(profileUser.getFollowingCount()));

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && currentUser.getUid().equals(userId)) {
            binding.btnEditProfile.setVisibility(View.VISIBLE);
            binding.btnFollow.setVisibility(View.GONE);
        } else {
            binding.btnEditProfile.setVisibility(View.GONE);
            binding.btnFollow.setVisibility(View.VISIBLE);
            if (currentUser != null) {
                checkFollowStatus();
            } else {
                binding.btnFollow.setVisibility(View.GONE);
            }
        }
    }

    private void checkFollowStatus() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            binding.btnFollow.setVisibility(View.GONE);
            return;
        }

        userRepository.getUserById(currentUser.getUid())
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        if (user != null && user.getFollowing() != null) {
                            boolean isFollowing = user.getFollowing().contains(userId);
                            updateFollowButton(isFollowing);
                        } else {
                            updateFollowButton(false);
                        }
                    } else {
                        updateFollowButton(false);
                    }
                })
                .addOnFailureListener(e -> {
                    updateFollowButton(false);
                    Log.e("ProfileActivity", "Error checking follow status: " + e.getMessage());
                });
    }

    private void updateFollowButton(boolean isFollowing) {
        if (isFollowing) {
            binding.btnFollow.setText(R.string.unfollow);
            binding.btnFollow.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.transparent)));
            binding.btnFollow.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.logo_orange)));
            binding.btnFollow.setStrokeWidth(getResources().getDimensionPixelSize(R.dimen.button_stroke_width));
            binding.btnFollow.setTextColor(ContextCompat.getColor(this, R.color.logo_orange));
        } else {
            binding.btnFollow.setText(R.string.follow);
            binding.btnFollow.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.logo_orange)));
            binding.btnFollow.setStrokeColor(null);
            binding.btnFollow.setStrokeWidth(0);
            binding.btnFollow.setTextColor(ContextCompat.getColor(this, R.color.white));
        }
        binding.btnFollow.setIcon(null);
    }

    private void loadUserPosts() {
        showLoading(true);
        Query query = postRepository.getPostsByUser(userId);

        query.addSnapshotListener((queryDocumentSnapshots, e) -> {
            showLoading(false);
            if (e != null) {
                Toast.makeText(this, "포스트를 로드하는 중 오류가 발생했습니다: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
                Log.e("ProfileActivity", "Error loading posts: " + e.getMessage());
                return;
            }

            postList.clear();

            if (queryDocumentSnapshots != null && !queryDocumentSnapshots.isEmpty()) {
                for (DocumentSnapshot document : queryDocumentSnapshots.getDocuments()) {
                    Post post = document.toObject(Post.class);
                    if (post != null) {
                        postList.add(post);
                    }
                }
            }

            adapter.notifyDataSetChanged();
            binding.tvPostsCount.setText(String.valueOf(postList.size()));
            binding.tvEmptyPosts.setVisibility(postList.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void setupListeners() {
        binding.btnFollow.setOnClickListener(v -> toggleFollow());

        binding.btnEditProfile.setOnClickListener(v -> {
            Intent intent = new Intent(this, EditProfileActivity.class);
            editProfileLauncher.launch(intent);
        });

        binding.layoutFollowers.setOnClickListener(v -> {
            if (profileUser == null) return;
            Intent intent = new Intent(this, FollowListActivity.class);
            intent.putExtra(FollowListActivity.EXTRA_USER_ID, profileUser.getUserId());
            intent.putExtra(FollowListActivity.EXTRA_LIST_TYPE, FollowListActivity.TYPE_FOLLOWERS);
            startActivity(intent);
        });

        binding.layoutFollowing.setOnClickListener(v -> {
            if (profileUser == null) return;
            Intent intent = new Intent(this, FollowListActivity.class);
            intent.putExtra(FollowListActivity.EXTRA_USER_ID, profileUser.getUserId());
            intent.putExtra(FollowListActivity.EXTRA_LIST_TYPE, FollowListActivity.TYPE_FOLLOWING);
            startActivity(intent);
        });
    }

    private void toggleFollow() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
            navigateToLogin();
            return;
        }

        String currentUserId = currentUser.getUid();

        if (currentUserId.equals(userId)) {
            return;
        }
        if (profileUser == null) {
            Toast.makeText(this, "사용자 정보를 가져올 수 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);

        userRepository.isFollowing(currentUserId, userId)
            .addOnSuccessListener(isFollowing -> {
                if (isFollowing) {
                    userRepository.unfollowUser(currentUserId, userId)
                            .addOnSuccessListener(aVoid -> {
                                updateFollowButton(false);
                                profileUser.setFollowerCount(Math.max(0, profileUser.getFollowerCount() - 1));
                                binding.tvFollowersCount.setText(String.valueOf(profileUser.getFollowerCount()));
                                Toast.makeText(ProfileActivity.this, "언팔로우했습니다.", Toast.LENGTH_SHORT).show();
                                showLoading(false);
                            })
                            .addOnFailureListener(unfollowError -> {
                                showLoading(false);
                                Toast.makeText(this, "언팔로우 중 오류: " + unfollowError.getMessage(), Toast.LENGTH_SHORT).show();
                                Log.e("ProfileActivity", "Error unfollowing: " + unfollowError.getMessage());
                            });
                } else {
                    userRepository.followUser(currentUserId, userId)
                            .addOnSuccessListener(aVoid -> {
                                updateFollowButton(true);
                                profileUser.setFollowerCount(profileUser.getFollowerCount() + 1);
                                binding.tvFollowersCount.setText(String.valueOf(profileUser.getFollowerCount()));
                                Toast.makeText(ProfileActivity.this, "팔로우했습니다.", Toast.LENGTH_SHORT).show();
                                showLoading(false);
                            })
                            .addOnFailureListener(followError -> {
                                showLoading(false);
                                Toast.makeText(this, "팔로우 중 오류: " + followError.getMessage(), Toast.LENGTH_SHORT).show();
                                Log.e("ProfileActivity", "Error following: " + followError.getMessage());
                            });
                }
            })
            .addOnFailureListener(e -> {
                showLoading(false);
                Toast.makeText(this, "팔로우 상태 확인 중 오류: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e("ProfileActivity", "Error checking if following: " + e.getMessage());
            });
    }

    private void showLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }
}

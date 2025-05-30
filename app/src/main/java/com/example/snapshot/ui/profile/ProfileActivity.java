package com.example.snapshot.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;

import com.bumptech.glide.Glide;
import com.example.snapshot.R;
import com.example.snapshot.databinding.ActivityProfileBinding;
import com.example.snapshot.model.Post;
import com.example.snapshot.model.User;
import com.example.snapshot.repository.PostRepository;
import com.example.snapshot.repository.UserRepository;
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

        setupToolbar();

        userId = getIntent().getStringExtra(EXTRA_USER_ID);
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
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null && currentUser.getUid().equals(userId)) {
                getSupportActionBar().setTitle(getString(R.string.title_my_profile));
            } else {
                getSupportActionBar().setTitle(getString(R.string.title_user_profile));
            }
        }
        binding.toolbar.setNavigationOnClickListener(v -> onBackPressed());
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
            checkFollowStatus();
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
                        }
                    }
                });
    }

    private void updateFollowButton(boolean isFollowing) {
        if (isFollowing) {
            binding.btnFollow.setText(R.string.unfollow);
            binding.btnFollow.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, android.R.color.transparent)));
            binding.btnFollow.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.logo_orange)));
            binding.btnFollow.setStrokeWidth(getResources().getDimensionPixelSize(R.dimen.button_stroke_width));
            binding.btnFollow.setTextColor(ContextCompat.getColor(this, R.color.logo_orange));
            binding.btnFollow.setIcon(null);
        } else {
            binding.btnFollow.setText(R.string.follow);
            binding.btnFollow.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.logo_orange)));
            binding.btnFollow.setStrokeColor(null);
            binding.btnFollow.setStrokeWidth(0);
            binding.btnFollow.setTextColor(ContextCompat.getColor(this, R.color.white));
            binding.btnFollow.setIcon(null);
        }
    }

    private void loadUserPosts() {
        Query query = postRepository.getPostsByUser(userId);

        query.addSnapshotListener((queryDocumentSnapshots, e) -> {
            if (e != null) {
                showLoading(false);
                Toast.makeText(this, "포스트를 로드하는 중 오류가 발생했습니다: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
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

            showLoading(false);
        });
    }

    private void setupListeners() {
        binding.btnFollow.setOnClickListener(v -> toggleFollow());

        binding.btnEditProfile.setOnClickListener(v -> {
            Intent intent = new Intent(this, EditProfileActivity.class);
            editProfileLauncher.launch(intent);
        });

        binding.layoutFollowers.setOnClickListener(v -> {
            Intent intent = new Intent(this, FollowListActivity.class);
            intent.putExtra(FollowListActivity.EXTRA_USER_ID, userId);
            intent.putExtra(FollowListActivity.EXTRA_LIST_TYPE, FollowListActivity.TYPE_FOLLOWERS);
            startActivity(intent);
        });

        binding.layoutFollowing.setOnClickListener(v -> {
            Intent intent = new Intent(this, FollowListActivity.class);
            intent.putExtra(FollowListActivity.EXTRA_USER_ID, userId);
            intent.putExtra(FollowListActivity.EXTRA_LIST_TYPE, FollowListActivity.TYPE_FOLLOWING);
            startActivity(intent);
        });
    }

    private void toggleFollow() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        String currentUserId = currentUser.getUid();

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
                                userRepository.unfollowUser(currentUserId, userId)
                                        .addOnSuccessListener(aVoid -> {
                                            updateFollowButton(false);
                                            if (profileUser != null) {
                                                profileUser.setFollowerCount(Math.max(0, profileUser.getFollowerCount() - 1));
                                                binding.tvFollowersCount.setText(String.valueOf(profileUser.getFollowerCount()));
                                            }
                                            showLoading(false);
                                        })
                                        .addOnFailureListener(e -> {
                                            showLoading(false);
                                            Toast.makeText(this, "언팔로우 중 오류가 발생했습니다: " + e.getMessage(),
                                                    Toast.LENGTH_SHORT).show();
                                        });
                            } else {
                                userRepository.followUser(currentUserId, userId)
                                        .addOnSuccessListener(aVoid -> {
                                            updateFollowButton(true);
                                            if (profileUser != null) {
                                                profileUser.setFollowerCount(profileUser.getFollowerCount() + 1);
                                                binding.tvFollowersCount.setText(String.valueOf(profileUser.getFollowerCount()));
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
}

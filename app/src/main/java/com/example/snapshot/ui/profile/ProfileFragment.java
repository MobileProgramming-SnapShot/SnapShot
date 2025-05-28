package com.example.snapshot.ui.profile;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager; // Added for horizontal RecyclerView

import com.bumptech.glide.Glide;
import com.example.snapshot.R;
import com.example.snapshot.databinding.ActivityProfileBinding; // Corrected: Assuming your XML is activity_profile.xml
import com.example.snapshot.model.Post;
import com.example.snapshot.model.User;
import com.example.snapshot.model.Tag; // Added for Tag model
import com.example.snapshot.repository.PostRepository;
import com.example.snapshot.repository.UserRepository;
import com.example.snapshot.repository.TagRepository; // Added for TagRepository
import com.example.snapshot.ui.home.TagAdapter; // Added for TagAdapter
import com.example.snapshot.ui.auth.LoginActivity; // Added if needed for logout navigation
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Query; // Added for PostRepository query

import java.util.ArrayList;
import java.util.List;

public class ProfileFragment extends Fragment {

    private ActivityProfileBinding binding; // Corrected binding type
    private UserRepository userRepository;
    private PostRepository postRepository;
    private TagRepository tagRepository;

    private String userId; // User ID of the profile being viewed
    private User profileUser; // The User object for the profile being viewed
    private ProfilePostAdapter postAdapter; // Renamed for clarity
    private TagAdapter tagAdapter;
    private List<Post> postList = new ArrayList<>();
    private List<Tag> savedTagList = new ArrayList<>();

    private ActivityResultLauncher<Intent> editProfileLauncher;

    public static final String EXTRA_USER_ID = "extra_user_id";

    public static ProfileFragment newInstance(String userId) {
        ProfileFragment fragment = new ProfileFragment();
        Bundle args = new Bundle();
        args.putString(EXTRA_USER_ID, userId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        editProfileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        // Reload profile data if EditProfileActivity returns OK
                        // Use the current user's ID as this fragment is for the *current* user's profile
                        FirebaseUser firebaseUser = FirebaseAuth.getInstance().getCurrentUser();
                        if (firebaseUser != null) {
                            loadUserProfile(); // Re-load profile to update UI
                        }
                    }
                }
        );

        userRepository = UserRepository.getInstance();
        postRepository = PostRepository.getInstance();
        tagRepository = TagRepository.getInstance();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = ActivityProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Get userId from arguments or current user (this logic needs to be robust)
        if (getArguments() != null) {
            userId = getArguments().getString(EXTRA_USER_ID);
        }

        // If no userId is passed, it means we're viewing the current user's profile
        if (userId == null || userId.isEmpty()) {
            FirebaseUser currentUserFirebase = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUserFirebase != null) {
                userId = currentUserFirebase.getUid();
            } else {
                Toast.makeText(getContext(), "사용자 정보를 찾을 수 없습니다. 로그인하십시오.", Toast.LENGTH_SHORT).show();
                // Optionally navigate to login or close the fragment
                if (getParentFragmentManager() != null) {
                    getParentFragmentManager().popBackStack();
                }
                return;
            }
        }

        setupToolbar();
        setupRecyclerViews();
        loadUserProfile(); // This will now fetch data for 'profileUser' (the user whose profile is shown)
        loadUserPosts();
        loadSavedTags(userId); // Load saved tags for the user whose profile is being displayed
        setupListeners();
    }

    private void setupToolbar() {
        binding.toolbar.setTitle("프로필");
        binding.toolbar.setNavigationIcon(R.drawable.ic_back_arrow); // Ensure this drawable exists
        binding.toolbar.setNavigationOnClickListener(v -> {
            if (getParentFragmentManager() != null) {
                getParentFragmentManager().popBackStack();
            }
        });
        // Add a settings icon if you want a settings button in the toolbar
        binding.toolbar.inflateMenu(R.menu.profile_menu); // Assuming you have a menu resource named profile_menu.xml
        binding.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_settings) { // Assuming your menu item ID is 'action_settings'
                showSettingsOptions();
                return true;
            }
            return false;
        });
    }

    private void setupRecyclerViews() {
        // Posts RecyclerView
        postAdapter = new ProfilePostAdapter(getContext(), postList); // Using postAdapter
        binding.recyclerPosts.setLayoutManager(new GridLayoutManager(getContext(), 3));
        binding.recyclerPosts.setAdapter(postAdapter);

        // Saved Tags RecyclerView
        tagAdapter = new TagAdapter(getContext(), savedTagList, true); // Assuming 'true' for selectable if needed
        binding.recyclerSavedTags.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.recyclerSavedTags.setAdapter(tagAdapter);

        tagAdapter.setOnTagClickListener(position -> {
            if (position >= 0 && position < savedTagList.size()) {
                Tag tag = savedTagList.get(position);
                navigateToTagDetail(tag.getTagId());
            }
        });
    }

    private void loadUserProfile() {
        showLoading(true);

        userRepository.getUserById(userId)
                .addOnSuccessListener(documentSnapshot -> {
                    showLoading(false);
                    if (documentSnapshot.exists()) {
                        profileUser = documentSnapshot.toObject(User.class);
                        if (profileUser != null) {
                            updateProfileUI(profileUser); // Pass the loaded user object
                        }
                    } else {
                        Toast.makeText(getContext(), "사용자 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
                        if (getParentFragmentManager() != null) {
                            getParentFragmentManager().popBackStack();
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Log.e("ProfileFragment", "Error loading user profile: " + e.getMessage());
                    Toast.makeText(getContext(), "사용자 정보를 로드하는 중 오류가 발생했습니다: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                    if (getParentFragmentManager() != null) {
                        getParentFragmentManager().popBackStack();
                    }
                });
    }

    private void updateProfileUI(User user) { // Accept User object as parameter
        binding.tvUsername.setText(user.getUsername());

        if (user.getProfilePicUrl() != null && !user.getProfilePicUrl().isEmpty()) {
            Glide.with(this)
                    .load(user.getProfilePicUrl())
                    .placeholder(R.drawable.default_profile) // Ensure default_profile exists
                    .error(R.drawable.default_profile) // Ensure default_profile exists
                    .into(binding.ivProfilePic); // Corrected ID: iv_profile_pic
        } else {
            binding.ivProfilePic.setImageResource(R.drawable.default_profile); // Corrected ID: iv_profile_pic
        }

        // Post count will be updated by loadUserPosts
        binding.tvFollowersCount.setText(String.valueOf(user.getFollowerCount())); // Corrected ID: tv_followers_count
        binding.tvFollowingCount.setText(String.valueOf(user.getFollowingCount())); // Corrected ID: tv_following_count

        if (user.getBio() != null && !user.getBio().isEmpty()) {
            binding.tvBio.setText(user.getBio());
            binding.tvBio.setVisibility(View.VISIBLE);
        } else {
            binding.tvBio.setVisibility(View.GONE);
        }

        FirebaseUser currentUserFirebase = FirebaseAuth.getInstance().getCurrentUser();
        // Control visibility of the correct Group elements
        if (currentUserFirebase != null && currentUserFirebase.getUid().equals(userId)) {
            binding.groupOwnerButtons.setVisibility(View.VISIBLE);
            binding.groupOtherUserButtons.setVisibility(View.GONE);
        } else {
            binding.groupOwnerButtons.setVisibility(View.GONE);
            binding.groupOtherUserButtons.setVisibility(View.VISIBLE);
            checkFollowStatus();
        }
    }

    private void checkFollowStatus() {
        FirebaseUser currentUserFirebase = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUserFirebase == null) {
            binding.btnFollow.setVisibility(View.GONE);
            return;
        }

        userRepository.getUserById(currentUserFirebase.getUid())
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
            binding.btnFollow.setText(R.string.unfollow); // Ensure R.string.unfollow exists
            // Consider setting a different background/text color for 'unfollow' state
            // Example: binding.btnFollow.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.some_gray_color)));
        } else {
            binding.btnFollow.setText(R.string.follow); // Ensure R.string.follow exists
            // Example: binding.btnFollow.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(getContext(), R.color.primary)));
        }
    }

    private void loadUserPosts() {
        Query query = postRepository.getPostsByUser(userId);

        query.addSnapshotListener((queryDocumentSnapshots, e) -> {
            if (e != null) {
                showLoading(false);
                Log.e("ProfileFragment", "Error loading user posts: " + e.getMessage());
                Toast.makeText(getContext(), "포스트를 로드하는 중 오류가 발생했습니다: " + e.getMessage(),
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

            postAdapter.notifyDataSetChanged(); // Using postAdapter
            binding.tvPostsCount.setText(String.valueOf(postList.size())); // Corrected ID: tv_posts_count
            binding.tvEmptyPosts.setVisibility(postList.isEmpty() ? View.VISIBLE : View.GONE); // Corrected ID: tv_empty_posts
            showLoading(false); // Only hide main loading after posts are loaded
        });
    }

    /**
     * Loads the saved tags for the user.
     * @param userId The ID of the user whose saved tags are to be loaded.
     */
    private void loadSavedTags(String userId) {
        Log.d("ProfileFragment", "Starting to load saved tags for user ID: " + userId);

        if (binding != null) {
            binding.progressBarTags.setVisibility(View.VISIBLE); // Corrected ID: progress_bar_tags
            binding.tvNoSavedTags.setVisibility(View.GONE); // Corrected ID: tv_no_saved_tags
        }

        savedTagList.clear(); // Clear existing tags before loading new ones
        tagAdapter.notifyDataSetChanged(); // Notify adapter to reflect empty state immediately

        tagRepository.getSavedTagsByUser(userId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<String> savedTagIds = new ArrayList<>();
                    for (DocumentSnapshot document : queryDocumentSnapshots) {
                        String tagId = document.getString("tagId");
                        if (tagId != null) {
                            savedTagIds.add(tagId);
                        }
                    }

                    Log.d("ProfileFragment", "Found " + savedTagIds.size() + " saved tag IDs.");

                    if (savedTagIds.isEmpty()) {
                        updateSavedTagsUI();
                        if (binding != null) {
                            binding.progressBarTags.setVisibility(View.GONE); // Corrected ID
                        }
                    } else {
                        loadTagsByIds(savedTagIds);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("ProfileFragment", "Failed to query saved tags: " + e.getMessage());
                    if (binding != null) {
                        binding.progressBarTags.setVisibility(View.GONE); // Corrected ID
                        Toast.makeText(getContext(), "저장된 태그를 불러오는 중 오류가 발생했습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Loads tag details based on a list of tag IDs. Handles Firebase's 10-item limit for `whereIn` queries.
     * @param tagIds A list of tag IDs to fetch.
     */
    private void loadTagsByIds(List<String> tagIds) {
        Log.d("ProfileFragment", "Starting to load tag info for " + tagIds.size() + " tags.");

        if (tagIds.isEmpty()) {
            updateSavedTagsUI();
            if (binding != null) {
                binding.progressBarTags.setVisibility(View.GONE); // Corrected ID
            }
            return;
        }

        final int batchSize = 10;
        int batchCount = (tagIds.size() + batchSize - 1) / batchSize;
        final int[] completedBatches = {0};

        for (int i = 0; i < batchCount; i++) {
            int startIndex = i * batchSize;
            int endIndex = Math.min((i + 1) * batchSize, tagIds.size());
            List<String> batchIds = tagIds.subList(startIndex, endIndex);

            Log.d("ProfileFragment", "Processing batch " + (i + 1) + "/" + batchCount + " with " + batchIds.size() + " tags.");

            tagRepository.getTagsByIds(batchIds)
                    .addOnSuccessListener(querySnapshot -> {
                        completedBatches[0]++;
                        int tagsAddedThisBatch = 0;

                        for (DocumentSnapshot document : querySnapshot) {
                            Tag tag = document.toObject(Tag.class);
                            if (tag != null) {
                                boolean exists = false;
                                for (Tag existingTag : savedTagList) {
                                    if (existingTag.getTagId().equals(tag.getTagId())) {
                                        exists = true;
                                        break;
                                    }
                                }
                                if (!exists) {
                                    savedTagList.add(tag);
                                    tagsAddedThisBatch++;
                                }
                            }
                        }

                        Log.d("ProfileFragment", "Batch " + completedBatches[0] + "/" + batchCount + " completed. Added " + tagsAddedThisBatch + " new tags.");

                        if (binding != null && completedBatches[0] >= batchCount) {
                            Log.d("ProfileFragment", "All batches processed. Total saved tags: " + savedTagList.size());
                            tagAdapter.notifyDataSetChanged();
                            updateSavedTagsUI();
                            binding.progressBarTags.setVisibility(View.GONE); // Corrected ID
                        }
                    })
                    .addOnFailureListener(e -> {
                        completedBatches[0]++;
                        Log.e("ProfileFragment", "Batch " + completedBatches[0] + "/" + batchCount + " failed: " + e.getMessage());

                        if (binding != null) {
                            Toast.makeText(getContext(), "태그 정보를 불러오는 중 오류가 발생했습니다: " + e.getMessage(), Toast.LENGTH_SHORT).show();

                            if (completedBatches[0] >= batchCount) {
                                tagAdapter.notifyDataSetChanged();
                                updateSavedTagsUI();
                                binding.progressBarTags.setVisibility(View.GONE); // Corrected ID
                            }
                        }
                    });
        }
    }

    /**
     * Updates the UI for the saved tags section based on the `savedTagList`.
     */
    private void updateSavedTagsUI() {
        if (binding == null) {
            Log.w("ProfileFragment", "updateSavedTagsUI: binding is null.");
            return;
        }

        Log.d("ProfileFragment", "Updating saved tags UI. Current tag count: " + savedTagList.size());

        if (savedTagList.isEmpty()) {
            binding.tvNoSavedTags.setVisibility(View.VISIBLE); // Corrected ID
            binding.recyclerSavedTags.setVisibility(View.GONE); // Corrected ID
            Log.d("ProfileFragment", "Displaying 'No saved tags' message.");
        } else {
            binding.tvNoSavedTags.setVisibility(View.GONE); // Corrected ID
            binding.recyclerSavedTags.setVisibility(View.VISIBLE); // Corrected ID
            Log.d("ProfileFragment", "Displaying saved tags RecyclerView.");

            binding.recyclerSavedTags.post(() -> {
                tagAdapter.notifyDataSetChanged();
                binding.recyclerSavedTags.requestLayout();
                binding.recyclerSavedTags.invalidate();
            });
        }
    }

    /**
     * Navigates to the Tag Detail screen.
     * @param tagId The ID of the tag to display.
     */
    private void navigateToTagDetail(String tagId) {
        // Ensure you have a TagDetailActivity in this package or provide the full path
        Intent intent = new Intent(getContext(), com.example.snapshot.ui.tag.TagDetailActivity.class);
        intent.putExtra(com.example.snapshot.ui.tag.TagDetailActivity.EXTRA_TAG_ID, tagId);
        startActivity(intent);
    }

    private void setupListeners() {
        binding.btnFollow.setOnClickListener(v -> toggleFollow());

        binding.btnEditProfile.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), EditProfileActivity.class);
            editProfileLauncher.launch(intent);
        });

        // Your XML has layout_followers and layout_following, not tvFollowerCount/tvFollowingCount for click listeners
        binding.layoutFollowers.setOnClickListener(v -> {
            if (profileUser != null) { // Use profileUser
                Intent intent = new Intent(getContext(), FollowListActivity.class);
                intent.putExtra(FollowListActivity.EXTRA_USER_ID, profileUser.getUserId());
                intent.putExtra(FollowListActivity.EXTRA_LIST_TYPE, FollowListActivity.TYPE_FOLLOWERS);
                startActivity(intent);
            }
        });

        binding.layoutFollowing.setOnClickListener(v -> {
            if (profileUser != null) { // Use profileUser
                Intent intent = new Intent(getContext(), FollowListActivity.class);
                intent.putExtra(FollowListActivity.EXTRA_USER_ID, profileUser.getUserId());
                intent.putExtra(FollowListActivity.EXTRA_LIST_TYPE, FollowListActivity.TYPE_FOLLOWING);
                startActivity(intent);
            }
        });

        // There is no btn_settings in your XML, so this part is commented out or needs a corresponding XML button
        // If you intended to add a settings button to the toolbar, refer to setupToolbar() method.
        // binding.btnSettings.setOnClickListener(v -> {
        //     showSettingsOptions();
        // });
    }

    private void toggleFollow() {
    }

    private void showSettingsOptions() {
        // Example: Implement a logout function or show a dialog with settings options
        // For now, just a logout example
        FirebaseAuth.getInstance().signOut(); // Firebase sign out
        navigateToLogin(); // Navigate to login screen
    }

    private void navigateToLogin() {
        Intent intent = new Intent(getContext(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        if (getActivity() != null) {
            getActivity().finish(); // Finish the current activity
        }
    }

    private void showLoading(boolean isLoading) {
        if (binding != null) {
            binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
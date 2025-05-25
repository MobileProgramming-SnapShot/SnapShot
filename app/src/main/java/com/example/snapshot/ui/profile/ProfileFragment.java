package com.example.snapshot.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import com.bumptech.glide.Glide;
import com.example.snapshot.R;
import com.example.snapshot.databinding.FragmentProfileBinding;
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

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;

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
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        editProfileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == AppCompatActivity.RESULT_OK) {
                        loadUserProfile();
                    }
                }
        );

        userRepository = UserRepository.getInstance();
        postRepository = PostRepository.getInstance();

        setupToolbar();

        if (getArguments() != null) {
            userId = getArguments().getString(EXTRA_USER_ID);
        }

        if (userId == null || userId.isEmpty()) {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null) {
                userId = currentUser.getUid();
            } else {
                Toast.makeText(getContext(), "사용자 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        setupRecyclerView();

        loadUserProfile();

        loadUserPosts();

        setupListeners();
    }

    private void setupToolbar() {
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && !currentUser.getUid().equals(userId)) {
            inflater.inflate(R.menu.menu_profile, menu);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_report_user) {
            showReportDialog();
            return true;
        }
        if (item.getItemId() == android.R.id.home) {
            requireActivity().onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupRecyclerView() {
        adapter = new ProfilePostAdapter(requireContext(), postList);
        binding.recyclerPosts.setLayoutManager(new GridLayoutManager(requireContext(), 3));
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
                        Toast.makeText(getContext(), "사용자 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
                    }
                    showLoading(false);
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(getContext(), "사용자 정보를 로드하는 중 오류가 발생했습니다: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void updateProfileUI() {
        binding.tvUsername.setText(profileUser.getUsername());

        if (profileUser.getProfilePicUrl() != null && !profileUser.getProfilePicUrl().isEmpty()) {
            Glide.with(requireContext())
                    .load(profileUser.getProfilePicUrl())
                    .placeholder(R.drawable.default_profile)
                    .error(R.drawable.default_profile)
                    .into(binding.ivProfilePic);
        }

        if (profileUser.getBio() != null && !profileUser.getBio().isEmpty()) {
            binding.etInterests.setText(profileUser.getBio());
            binding.etInterests.setVisibility(View.VISIBLE);
        } else {
            binding.etInterests.setText("");
            binding.etInterests.setVisibility(View.VISIBLE);
        }

        binding.btnFollowers.setText(getString(R.string.followers_count, profileUser.getFollowerCount()));
        binding.btnFollowing.setText(getString(R.string.following_count, profileUser.getFollowingCount()));

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && currentUser.getUid().equals(userId)) {
            binding.etInterests.setFocusableInTouchMode(true);
            binding.etInterests.setHint(R.string.hint_interests_editable);
        } else {
            binding.etInterests.setFocusable(false);
            binding.etInterests.setHint(R.string.hint_interests_readonly);
        }
    }

    private void loadUserPosts() {
        Query query = postRepository.getPostsByUser(userId);

        query.addSnapshotListener((queryDocumentSnapshots, firebaseFirestoreException) -> {
            if (firebaseFirestoreException != null) {
                showLoading(false);
                Toast.makeText(getContext(), "포스트를 로드하는 중 오류가 발생했습니다: " + firebaseFirestoreException.getMessage(),
                        Toast.LENGTH_SHORT).show();
                return;
            }

            if (queryDocumentSnapshots == null || queryDocumentSnapshots.isEmpty()) {
                postList.clear();
                adapter.notifyDataSetChanged();
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
            showLoading(false);
        });
    }

    private void setupListeners() {
        binding.btnFollowers.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), FollowListActivity.class);
            intent.putExtra(FollowListActivity.EXTRA_USER_ID, userId);
            intent.putExtra(FollowListActivity.EXTRA_LIST_TYPE, FollowListActivity.TYPE_FOLLOWERS);
            startActivity(intent);
        });

        binding.btnFollowing.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), FollowListActivity.class);
            intent.putExtra(FollowListActivity.EXTRA_USER_ID, userId);
            intent.putExtra(FollowListActivity.EXTRA_LIST_TYPE, FollowListActivity.TYPE_FOLLOWING);
            startActivity(intent);
        });

        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null && currentUser.getUid().equals(userId)) {
            binding.etInterests.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus) {
                    Toast.makeText(getContext(), "관심사 저장 (현재는 임시)", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void showLoading(boolean isLoading) {
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }

    private void showReportDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_report, null);
        builder.setView(dialogView);

        TextView titleText = dialogView.findViewById(R.id.title_report);
        titleText.setText(R.string.report_user_title);

        RadioGroup radioGroup = dialogView.findViewById(R.id.radio_group_report_reason);
        EditText customReasonEdit = dialogView.findViewById(R.id.edit_text_custom_reason);

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radio_other) {
                customReasonEdit.setVisibility(View.VISIBLE);
            } else {
                customReasonEdit.setVisibility(View.GONE);
            }
        });

        builder.setPositiveButton(R.string.report_submit, (dialogInterface, i) -> {
            int selectedId = radioGroup.getCheckedRadioButtonId();

            if (selectedId == -1) {
                Toast.makeText(requireContext(), R.string.report_reason, Toast.LENGTH_SHORT).show();
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

        builder.create().show();
    }

    private void submitReport(String reason) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(requireContext(), R.string.login_required, Toast.LENGTH_SHORT).show();
            return;
        }

        if (profileUser == null) {
            Toast.makeText(requireContext(), R.string.error_loading_post, Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);
        String reporterId = currentUser.getUid();

        ReportRepository reportRepository = ReportRepository.getInstance();
        reportRepository.reportUser(reporterId, userId, reason)
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    Toast.makeText(requireContext(), R.string.report_success, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    if (e.getMessage() != null && e.getMessage().contains("이미 이 사용자를 신고하셨습니다")) {
                        Toast.makeText(requireContext(), R.string.report_already_reported, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.report_failed) + ": " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
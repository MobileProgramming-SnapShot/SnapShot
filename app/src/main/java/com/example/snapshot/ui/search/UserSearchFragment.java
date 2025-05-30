package com.example.snapshot.ui.search;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.snapshot.R;
import com.example.snapshot.model.User;
import com.example.snapshot.repository.UserRepository;
import com.example.snapshot.ui.profile.ProfileActivity;
import com.example.snapshot.ui.profile.UserAdapter;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class UserSearchFragment extends Fragment {
    
    private UserRepository userRepository;
    private List<User> searchResults = new ArrayList<>();
    private UserAdapter adapter;
    private RecyclerView recyclerView;
    private TextView emptyView;
    private ProgressBar progressBar;
    
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_search_results, container, false);
        
        // 저장소 초기화
        userRepository = UserRepository.getInstance();
        
        // 뷰 찾기
        recyclerView = view.findViewById(R.id.recycler_search_results);
        emptyView = view.findViewById(R.id.tv_no_results);
        progressBar = view.findViewById(R.id.progress_bar);
        
        // 어댑터 설정
        adapter = new UserAdapter(requireContext(), searchResults, true);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
        
        return view;
    }
    
    public void search(String query) {
        if (userRepository == null) {
            userRepository = UserRepository.getInstance();
        }
        
        progressBar.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        searchResults.clear(); // 검색 시작 시 항상 이전 결과 초기화
        
        // 사용자 검색 - 이름으로 검색
        Query nameQuery = userRepository.searchUsersByName(query);
        
        nameQuery.get()
                .addOnSuccessListener(nameQueryDocumentSnapshots -> {
                    Set<User> uniqueUsers = new HashSet<>(); // 중복 제거를 위한 Set
                    for (DocumentSnapshot document : nameQueryDocumentSnapshots) {
                        User user = document.toObject(User.class);
                        if (user != null) {
                            uniqueUsers.add(user);
                        }
                    }
                    
                    // 이메일로도 검색
                    Query emailQuery = userRepository.searchUsersByEmail(query);
                    emailQuery.get()
                            .addOnSuccessListener(emailQueryDocumentSnapshots -> {
                                for (DocumentSnapshot document : emailQueryDocumentSnapshots) {
                                    User user = document.toObject(User.class);
                                    if (user != null) {
                                        uniqueUsers.add(user); // Set에 추가하여 중복 자동 처리
                                    }
                                }
                                searchResults.clear();
                                searchResults.addAll(new ArrayList<>(uniqueUsers)); // 최종 결과를 리스트에 추가
                                updateSearchResultsView();
                            })
                            .addOnFailureListener(e -> {
                                // 이름 검색 결과만이라도 보여주기 위해 현재까지의 uniqueUsers를 사용
                                searchResults.clear();
                                searchResults.addAll(new ArrayList<>(uniqueUsers));
                                updateSearchResultsView();
                                Toast.makeText(requireContext(), R.string.error_search_email, Toast.LENGTH_SHORT).show();
                            });
                })
                .addOnFailureListener(e -> {
                    // 이름 검색 실패 시 이메일로만 검색 시도
                    Query emailQuery = userRepository.searchUsersByEmail(query);
                    emailQuery.get()
                            .addOnSuccessListener(emailQueryDocumentSnapshots -> {
                                Set<User> uniqueUsers = new HashSet<>();
                                for (DocumentSnapshot document : emailQueryDocumentSnapshots) {
                                    User user = document.toObject(User.class);
                                    if (user != null) {
                                        uniqueUsers.add(user);
                                    }
                                }
                                searchResults.clear();
                                searchResults.addAll(new ArrayList<>(uniqueUsers));
                                updateSearchResultsView();
                            })
                            .addOnFailureListener(emailError -> {
                                progressBar.setVisibility(View.GONE);
                                emptyView.setVisibility(View.VISIBLE);
                                Toast.makeText(requireContext(), R.string.error_network, Toast.LENGTH_SHORT).show();
                            });
                });
    }
    
    private void updateSearchResultsView() {
        progressBar.setVisibility(View.GONE);
        
        if (searchResults.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();
        }
    }

    // 검색 결과 지우기
    public void clearResults() {
        if (searchResults != null) {
            searchResults.clear();
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
        if (emptyView != null) {
            emptyView.setVisibility(View.GONE); // '결과 없음' 텍스트 숨기기
        }
        if (recyclerView != null) {
            recyclerView.setVisibility(View.GONE); // 리사이클러뷰 숨기기
        }
        if (progressBar != null) {
            progressBar.setVisibility(View.GONE); // 로딩 숨기기
        }
        // 사용자 검색 프래그먼트는 초기 상태 로드가 필요 없을 수 있음
        // 필요하다면 여기에 초기 데이터 로드 로직 추가
    }
} 
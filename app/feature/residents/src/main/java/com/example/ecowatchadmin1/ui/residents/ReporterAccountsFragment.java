package com.example.ecowatchadmin1.ui.residents;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.ResidentAccount;
import com.example.ecowatchadmin1.ui.nav.DashboardNavigator;
import com.example.ecowatchadmin1.utils.SessionManager;
import com.google.android.material.chip.Chip;

import java.util.List;

/** Searchable, filterable list of server-backed reporter accounts. */
public class ReporterAccountsFragment extends Fragment {
    private static final long SEARCH_DEBOUNCE_MS = 350L;

    private ReporterAccountsViewModel viewModel;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private ReporterAccountAdapter adapter;
    private EditText searchEditText;
    private Chip filterAll;
    private Chip filterActive;
    private Chip filterDisabled;
    private TextView resultCount;
    private TextView emptyText;
    private View loadMoreButton;
    private View btnBack;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private String selectedStatus = "ALL";
    private boolean firstResume = true;
    private boolean authorized;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_reporter_accounts, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        setupBack();
        viewModel = new ViewModelProvider(this).get(ReporterAccountsViewModel.class);

        SessionManager sessionManager = new SessionManager(requireContext());
        if (!sessionManager.isAdmin() || !sessionManager.isLoggedIn()) {
            authorized = false;
            showAdminOnlyState();
            return;
        }
        authorized = true;

        setupList();
        setupSearch();
        setupFilters();
        observeViewModel();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel == null || !authorized) return;
        if (firstResume) {
            firstResume = false;
            viewModel.loadResidents(searchEditText.getText().toString(), selectedStatus);
        } else {
            viewModel.refreshResidents();
        }
    }

    @Override
    public void onPause() {
        searchHandler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    private void bindViews(View view) {
        btnBack = view.findViewById(R.id.btnBack);
        swipeRefresh = view.findViewById(R.id.reporterAccountsSwipeRefresh);
        recyclerView = view.findViewById(R.id.reporterAccountsRecyclerView);
        searchEditText = view.findViewById(R.id.reporterAccountsSearch);
        filterAll = view.findViewById(R.id.reporterAccountsFilterAll);
        filterActive = view.findViewById(R.id.reporterAccountsFilterActive);
        filterDisabled = view.findViewById(R.id.reporterAccountsFilterDisabled);
        resultCount = view.findViewById(R.id.reporterAccountsResultCount);
        emptyText = view.findViewById(R.id.reporterAccountsEmptyText);
        loadMoreButton = view.findViewById(R.id.reporterAccountsLoadMore);
    }

    private void setupBack() {
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> {
                if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                    getParentFragmentManager().popBackStack();
                } else if (getActivity() instanceof DashboardNavigator) {
                    ((DashboardNavigator) getActivity()).navigateToHome();
                } else if (getActivity() != null) {
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            });
        }
    }

    private void showAdminOnlyState() {
        if (emptyText != null) {
            emptyText.setText(R.string.personnel_error_admin_only);
            emptyText.setVisibility(View.VISIBLE);
        }
        if (swipeRefresh != null) swipeRefresh.setEnabled(false);
    }

    private void setupList() {
        adapter = new ReporterAccountAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
        adapter.setListener(resident -> {
            if (resident == null || isBlank(resident.getResidentId())) return;
            getParentFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer,
                            ReporterDetailsFragment.newInstance(resident.getResidentId()))
                    .addToBackStack(null)
                    .commit();
        });

        swipeRefresh.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.green));
        swipeRefresh.setOnRefreshListener(() -> viewModel.refreshResidents());
        loadMoreButton.setOnClickListener(v -> viewModel.loadMore());
    }

    private void setupSearch() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchHandler.removeCallbacksAndMessages(null);
                searchHandler.postDelayed(() -> {
                    if (isAdded() && viewModel != null) {
                        viewModel.loadResidents(s.toString(), selectedStatus);
                    }
                }, SEARCH_DEBOUNCE_MS);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void setupFilters() {
        filterAll.setOnClickListener(v -> selectFilter("ALL", filterAll));
        filterActive.setOnClickListener(v -> selectFilter("ACTIVE", filterActive));
        filterDisabled.setOnClickListener(v -> selectFilter("DISABLED", filterDisabled));
    }

    private void selectFilter(String status, Chip selected) {
        selectedStatus = status;
        filterAll.setChecked(filterAll == selected);
        filterActive.setChecked(filterActive == selected);
        filterDisabled.setChecked(filterDisabled == selected);
        viewModel.loadResidents(searchEditText.getText().toString(), selectedStatus);
    }

    private void observeViewModel() {
        viewModel.getResidents().observe(getViewLifecycleOwner(), residents -> {
            List<ResidentAccount> values = residents == null
                    ? new java.util.ArrayList<>() : residents;
            adapter.setResidents(values);
            resultCount.setText(getResources().getQuantityString(
                    R.plurals.reporter_accounts_result_count, values.size(), values.size()));
            emptyText.setVisibility(values.isEmpty() ? View.VISIBLE : View.GONE);
        });

        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            if (swipeRefresh != null) swipeRefresh.setRefreshing(Boolean.TRUE.equals(loading));
        });

        viewModel.getLoadingMore().observe(getViewLifecycleOwner(), loadingMore -> {
            if (loadMoreButton != null) {
                loadMoreButton.setEnabled(!Boolean.TRUE.equals(loadingMore));
            }
        });

        viewModel.getHasMore().observe(getViewLifecycleOwner(), hasMore -> {
            if (loadMoreButton != null) {
                loadMoreButton.setVisibility(Boolean.TRUE.equals(hasMore)
                        ? View.VISIBLE : View.GONE);
            }
        });

        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message == null || message.trim().isEmpty()) return;
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            viewModel.clearError();
        });
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

package com.example.ecowatchadmin1.ui.residents;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.Report;
import com.example.ecowatchadmin1.data.models.ReportStatistics;
import com.example.ecowatchadmin1.data.models.ResidentAccount;
import com.example.ecowatchadmin1.ui.reports.ReportDetailDialog;
import com.example.ecowatchadmin1.utils.ContactActions;
import com.example.ecowatchadmin1.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/** Reporter profile, live backend statistics, and complete report history. */
public class ReporterDetailsFragment extends Fragment {
    private static final String ARG_RESIDENT_ID = "resident_id";
    private static final long LIVE_REFRESH_MS = 30_000L;

    private ReporterDetailsViewModel viewModel;
    private SwipeRefreshLayout swipeRefresh;
    private ShapeableImageView avatar;
    private TextView name;
    private TextView residentId;
    private TextView status;
    private TextView username;
    private TextView email;
    private TextView address;
    private TextView dateCreated;
    private TextView lastActive;
    private TextView contactPhone;
    private TextView contactEmail;
    private TextView contactMessenger;
    private View contactPhoneRow;
    private View contactEmailRow;
    private View contactMessengerRow;

    /** Raw contact values behind the rows; see {@link #bindContactRow}. */
    private String contactPhoneValue = "";
    private String contactEmailValue = "";
    private String contactMessengerValue = "";
    private TextView totalCount;
    private TextView pendingCount;
    private TextView inProgressCount;
    private TextView completedCount;
    private TextView deniedCount;
    private RecyclerView historyRecyclerView;
    private TextView historyEmpty;
    private View reportLoadMoreButton;
    private ReporterReportAdapter reportAdapter;
    private MaterialButton resetPasswordButton;
    private MaterialButton disableButton;
    private MaterialButton enableButton;
    private MaterialButton deleteButton;
    private TextView actionsHeader;

    /** The reporter currently on screen; the action buttons operate on this. */
    private ResidentAccount current;

    /** Holds the one-shot dialogs so a rotation does not leak them. */
    private AlertDialog actionDialog;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable liveRefresh = new Runnable() {
        @Override
        public void run() {
            if (isAdded() && viewModel != null && authorized) viewModel.refresh();
            refreshHandler.postDelayed(this, LIVE_REFRESH_MS);
        }
    };
    private boolean firstResume = true;
    private boolean authorized;

    public static ReporterDetailsFragment newInstance(String residentId) {
        ReporterDetailsFragment fragment = new ReporterDetailsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_RESIDENT_ID, residentId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_reporter_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        viewModel = new ViewModelProvider(this).get(ReporterDetailsViewModel.class);

        SessionManager sessionManager = new SessionManager(requireContext());
        String id = getResidentId();
        if (!sessionManager.isAdmin() || !sessionManager.isLoggedIn() || isBlank(id)) {
            firstResume = false;
            authorized = false;
            Toast.makeText(requireContext(), "Administrator access is required.", Toast.LENGTH_LONG).show();
            return;
        }
        authorized = true;

        setupBack(view);
        setupContactActions();
        setupHistory();
        setupActions();
        setupRefresh();
        observeViewModel();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel == null || !authorized) return;
        if (firstResume) {
            firstResume = false;
            viewModel.load(getResidentId(), true);
        } else {
            viewModel.refresh();
        }
        refreshHandler.removeCallbacks(liveRefresh);
        refreshHandler.postDelayed(liveRefresh, LIVE_REFRESH_MS);
    }

    @Override
    public void onPause() {
        refreshHandler.removeCallbacks(liveRefresh);
        super.onPause();
    }

    private void bindViews(View view) {
        swipeRefresh = view.findViewById(R.id.reporterDetailsSwipeRefresh);
        avatar = view.findViewById(R.id.reporterDetailsAvatar);
        name = view.findViewById(R.id.reporterDetailsName);
        residentId = view.findViewById(R.id.reporterDetailsResidentId);
        status = view.findViewById(R.id.reporterDetailsStatus);
        username = view.findViewById(R.id.reporterDetailsUsername);
        email = view.findViewById(R.id.reporterDetailsEmail);
        address = view.findViewById(R.id.reporterDetailsAddress);
        dateCreated = view.findViewById(R.id.reporterDetailsDateCreated);
        lastActive = view.findViewById(R.id.reporterDetailsLastActive);
        contactPhoneRow = view.findViewById(R.id.reporterContactPhoneRow);
        contactEmailRow = view.findViewById(R.id.reporterContactEmailRow);
        contactMessengerRow = view.findViewById(R.id.reporterContactMessengerRow);
        contactPhone = view.findViewById(R.id.residentPhoneNumber);
        contactEmail = view.findViewById(R.id.residentDetailsEmail);
        contactMessenger = view.findViewById(R.id.residentMessengerLink);
        totalCount = view.findViewById(R.id.reporterStatTotal);
        pendingCount = view.findViewById(R.id.reporterStatPending);
        inProgressCount = view.findViewById(R.id.reporterStatInProgress);
        completedCount = view.findViewById(R.id.reporterStatCompleted);
        deniedCount = view.findViewById(R.id.reporterStatDenied);
        historyRecyclerView = view.findViewById(R.id.reporterHistoryRecyclerView);
        historyEmpty = view.findViewById(R.id.reporterHistoryEmpty);
        reportLoadMoreButton = view.findViewById(R.id.reporterHistoryLoadMore);
        resetPasswordButton = view.findViewById(R.id.reporterDetailsResetPassword);
        disableButton = view.findViewById(R.id.reporterDetailsDisable);
        enableButton = view.findViewById(R.id.reporterDetailsEnable);
        deleteButton = view.findViewById(R.id.deleteReporter);
        actionsHeader = view.findViewById(R.id.reporterDetailsActionsHeader);
    }

    /**
     * Wires the account-management actions. Both read {@link #current} rather
     * than the arguments, so they always act on the account the screen is
     * actually showing.
     */
    private void setupActions() {
        resetPasswordButton.setOnClickListener(v -> {
            if (current != null) showResetDialog(current);
        });
        disableButton.setOnClickListener(v -> {
            if (current != null) confirmDisable(current);
        });
        enableButton.setOnClickListener(v -> {
            if (current != null) confirmEnable(current);
        });
        deleteButton.setOnClickListener(v -> {
            if (current != null) confirmDelete(current);
        });
    }

    private void setupBack(View view) {
        view.findViewById(R.id.reporterDetailsBack).setOnClickListener(v ->
                getParentFragmentManager().popBackStack());
    }

    /**
     * Wires the three contact rows to the phone, email and Messenger apps.
     *
     * <p>The click reads the last bound value rather than the TextView's text,
     * because a row with no value renders a "Not provided" placeholder and that
     * placeholder must never be handed to an intent.
     */
    private void setupContactActions() {
        contactPhoneRow.setOnClickListener(v ->
                ContactActions.dial(requireContext(), contactPhoneValue));
        contactEmailRow.setOnClickListener(v ->
                ContactActions.email(requireContext(), contactEmailValue));
        contactMessengerRow.setOnClickListener(v ->
                ContactActions.messenger(requireContext(), contactMessengerValue));
    }

    private void setupHistory() {
        reportAdapter = new ReporterReportAdapter();
        historyRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        historyRecyclerView.setAdapter(reportAdapter);
        reportAdapter.setListener(report -> ReportDetailDialog.showReadOnly(this, report));
        reportLoadMoreButton.setOnClickListener(v -> viewModel.loadMoreReports());
    }

    private void setupRefresh() {
        swipeRefresh.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.green));
        swipeRefresh.setOnRefreshListener(() -> viewModel.refresh());
    }

    private void observeViewModel() {
        viewModel.getResident().observe(getViewLifecycleOwner(), this::renderResident);
        viewModel.getStatistics().observe(getViewLifecycleOwner(), this::renderStatistics);
        viewModel.getReports().observe(getViewLifecycleOwner(), reports -> {
            java.util.List<Report> values = reports == null
                    ? new java.util.ArrayList<>() : reports;
            reportAdapter.setReports(values);
            historyEmpty.setVisibility(values.isEmpty() ? View.VISIBLE : View.GONE);
        });
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            if (swipeRefresh != null) swipeRefresh.setRefreshing(Boolean.TRUE.equals(loading));
        });

        viewModel.getLoadingReports().observe(getViewLifecycleOwner(), loading -> {
            if (reportLoadMoreButton != null) {
                reportLoadMoreButton.setEnabled(!Boolean.TRUE.equals(loading));
            }
        });

        viewModel.getHasMoreReports().observe(getViewLifecycleOwner(), hasMore -> {
            if (reportLoadMoreButton != null) {
                reportLoadMoreButton.setVisibility(Boolean.TRUE.equals(hasMore)
                        ? View.VISIBLE : View.GONE);
            }
        });
        viewModel.getError().observe(getViewLifecycleOwner(), message -> {
            if (message == null || message.trim().isEmpty()) return;
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            viewModel.clearError();
        });
        viewModel.getMessage().observe(getViewLifecycleOwner(), message -> {
            if (message == null || message.trim().isEmpty()) return;
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            viewModel.clearMessage();
        });
        // The temporary password is shown once and dropped immediately, so the
        // 30-second live refresh cannot replay a credential onto the screen.
        viewModel.getResetPassword().observe(getViewLifecycleOwner(), temporaryPassword -> {
            if (temporaryPassword == null || temporaryPassword.trim().isEmpty()) return;
            showPasswordHandoff(current, temporaryPassword);
            viewModel.clearResetPassword();
        });

        viewModel.getDeleting().observe(getViewLifecycleOwner(),
                deleting -> renderDeleting(Boolean.TRUE.equals(deleting)));

        // Consumed once. The value is cleared before popping so a rotation on the
        // way out cannot replay the deletion and pop the screen a second time.
        viewModel.getDeletedResidentId().observe(getViewLifecycleOwner(), deletedId -> {
            if (deletedId == null || deletedId.trim().isEmpty()) return;
            onReporterDeleted(deletedId);
            viewModel.clearDeletedResidentId();
        });
    }

    private void renderResident(ResidentAccount resident) {
        if (resident == null) return;
        current = resident;
        name.setText(resident.getFullName());
        residentId.setText(ReporterAccountUi.valueOrDash(resident.getResidentId()));
        ReporterAccountUi.bindStatus(status, resident);
        ReporterAccountUi.loadAvatar(avatar, resident.getProfileImageUrl());
        username.setText(ReporterAccountUi.valueOrDash(resident.getUsername()));
        email.setText(ReporterAccountUi.valueOrDash(resident.getEmail()));
        address.setText(ReporterAccountUi.valueOrDash(resident.getAddress()));
        dateCreated.setText(ReporterAccountUi.formatDate(resident.getDateCreated()));
        lastActive.setText(ReporterAccountUi.formatLastActive(resident.getLastActive()));

        String personName = resident.getFullName();
        contactPhoneValue = bindContactRow(contactPhoneRow, contactPhone,
                resident.getPhoneNumber(), personName,
                R.string.contact_phone_description, R.string.contact_no_phone_number);
        // The contact card repeats the address the profile block already shows,
        // so it reuses the account email rather than reading a second field.
        contactEmailValue = bindContactRow(contactEmailRow, contactEmail,
                resident.getEmail(), personName,
                R.string.contact_email_description, R.string.contact_no_email_address);
        contactMessengerValue = bindContactRow(contactMessengerRow, contactMessenger,
                resident.getMessengerLink(), personName,
                R.string.contact_messenger_description, R.string.contact_no_messenger_link);

        /*
         * Reset stays available either way: an administrator still needs to set
         * a password they can read out for a blocked reporter. Only the
         * disable/enable pair is mutually exclusive, so a disabled account
         * shows the way back rather than an empty Actions section.
         *
         * The heading hides only when the account no longer exists on the server
         * (a 404 renders no actions at all), which would otherwise leave an
         * "Actions" label above nothing.
         */
        boolean enabled = !resident.isDisabled();
        disableButton.setVisibility(enabled ? View.VISIBLE : View.GONE);
        enableButton.setVisibility(enabled ? View.GONE : View.VISIBLE);
        resetPasswordButton.setVisibility(View.VISIBLE);
        actionsHeader.setVisibility(View.VISIBLE);
    }

    /**
     * Asks for the new password and hands it to the ViewModel. The dialog stays
     * open on a validation failure so the administrator can correct a typo
     * rather than retyping the whole thing.
     */
    private void showResetDialog(ResidentAccount resident) {
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_reporter_reset_password, null, false);
        TextInputLayout passwordLayout = content.findViewById(R.id.resetPasswordLayout);
        TextInputLayout confirmLayout = content.findViewById(R.id.resetConfirmPasswordLayout);
        TextInputEditText password = content.findViewById(R.id.resetPassword);
        TextInputEditText confirm = content.findViewById(R.id.resetConfirmPassword);
        TextView label = content.findViewById(R.id.resetReporterLabel);
        label.setText(getString(R.string.reporter_resident_id_label) + ": "
                + ReporterAccountUi.valueOrDash(resident.getResidentId())
                + "\n" + resident.getFullName());

        actionDialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.reporter_reset, null)
                .create();
        actionDialog.setOnShowListener(dialog -> actionDialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    passwordLayout.setError(null);
                    confirmLayout.setError(null);
                    String passwordValue = inputValue(password);
                    String confirmValue = inputValue(confirm);
                    if (passwordValue.length() < 8) {
                        passwordLayout.setError(getString(R.string.reporter_error_password));
                        return;
                    }
                    if (!passwordValue.equals(confirmValue)) {
                        confirmLayout.setError(getString(R.string.reporter_error_confirm));
                        return;
                    }
                    // The server hands the temporary password back so it can be
                    // read to the reporter. It has to be shown in a copyable
                    // dialog rather than a toast, otherwise it cannot be relayed.
                    viewModel.resetPassword(resident, passwordValue);
                    actionDialog.dismiss();
                }));
        actionDialog.show();
    }

    /**
     * Shown once a reset succeeds, so the temporary password can be copied and
     * read out. Not cancelable: dismissing it is the acknowledgement that the
     * password has been passed on.
     */
    private void showPasswordHandoff(ResidentAccount resident, String temporaryPassword) {
        if (!isAdded() || resident == null || isBlank(temporaryPassword)) return;
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_reporter_credentials, null, false);
        TextView id = content.findViewById(R.id.credentialResidentId);
        TextView nameView = content.findViewById(R.id.credentialName);
        TextView addressView = content.findViewById(R.id.credentialAddress);
        TextView usernameView = content.findViewById(R.id.credentialUsername);
        TextView passwordText = content.findViewById(R.id.credentialPassword);
        id.setText(ReporterAccountUi.valueOrDash(resident.getResidentId()));
        nameView.setText(resident.getFullName());
        addressView.setText(ReporterAccountUi.valueOrDash(resident.getAddress()));
        usernameView.setText(ReporterAccountUi.valueOrDash(resident.getUsername()));
        passwordText.setText(temporaryPassword);

        actionDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.personnel_details_reset_password)
                .setView(content)
                .setPositiveButton(R.string.reporter_done, null)
                .setCancelable(false)
                .create();
        actionDialog.setCanceledOnTouchOutside(false);
        bindCopy(content, R.id.copyResidentId, id.getText().toString(),
                getString(R.string.reporter_resident_id_label));
        bindCopy(content, R.id.copyName, nameView.getText().toString(),
                getString(R.string.reporter_name_label));
        bindCopy(content, R.id.copyAddress, addressView.getText().toString(),
                getString(R.string.home_address_label));
        bindCopy(content, R.id.copyUsername, usernameView.getText().toString(),
                getString(R.string.username_label));
        bindCopy(content, R.id.copyPassword, passwordText.getText().toString(),
                getString(R.string.reporter_password_label));
        actionDialog.show();
    }

    /**
     * Confirms before the one action here that cannot be taken back.
     *
     * <p>Same shape as the resident app's account-deletion confirmation: the
     * Confirm and Cancel buttons are part of the dialog view, so Confirm can be
     * held disabled until the text matches exactly. An AlertDialog positive
     * button always dismisses the dialog and has no disabled state, which is why
     * the layout owns the buttons here.
     *
     * <p>The word to type is the reporter's username rather than DELETE, so the
     * administrator has to read who they are about to destroy instead of
     * pattern-matching a word they have typed all day. A plain Yes/No would be one
     * mis-tap from destroying an account and every report it owns, which is a
     * materially worse accident than the disable it sits beside — and that one has
     * an undo.
     */
    private void confirmDelete(ResidentAccount resident) {
        String name = resident.getFullName();
        String expected = resident.getUsername() == null ? "" : resident.getUsername().trim();

        // Nothing to match against, so a correct confirmation is impossible. Better
        // to say so than to accept any input.
        if (expected.isEmpty()) {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.reporter_delete_title)
                    .setMessage(getString(R.string.reporter_delete_no_username, name))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }

        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_reporter_delete, null, false);
        TextView message = dialogView.findViewById(R.id.reporterDeleteMessage);
        TextInputLayout inputLayout = dialogView.findViewById(R.id.reporterDeleteInputLayout);
        TextInputEditText input = dialogView.findViewById(R.id.reporterDeleteInput);
        MaterialButton confirmBtn = dialogView.findViewById(R.id.reporterDeleteBtn);
        MaterialButton cancelBtn = dialogView.findViewById(R.id.reporterDeleteCancelBtn);

        message.setText(getString(R.string.reporter_delete_consequence, name));
        inputLayout.setHint(getString(R.string.reporter_delete_confirm_hint, expected));

        actionDialog = new AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .create();
        if (actionDialog.getWindow() != null) {
            actionDialog.getWindow()
                    .setBackgroundDrawableResource(android.R.color.transparent);
        }
        actionDialog.setCancelable(false);
        actionDialog.setCanceledOnTouchOutside(false);

        confirmBtn.setEnabled(false);
        confirmBtn.setOnClickListener(v -> {
            actionDialog.dismiss();
            viewModel.deleteResident(resident);
        });
        cancelBtn.setOnClickListener(v -> actionDialog.dismiss());

        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                // contentEquals is an exact comparison, so "juan", "Juan " and
                // "juan!" all stay locked.
                confirmBtn.setEnabled(expected.contentEquals(s));
            }
        });

        actionDialog.show();
        input.requestFocus();
    }

    /**
     * Leaves the screen after a successful deletion.
     *
     * <p>Pops rather than showing a result page: the account it was showing no
     * longer exists, so there is nothing left to render, and the list underneath
     * refreshes on its own when it resumes.
     */
    private void onReporterDeleted(String id) {
        if (!isAdded() || isBlank(id)) return;
        String name = current == null ? id : current.getFullName();
        Toast.makeText(requireContext(),
                getString(R.string.reporter_deleted_confirmation, name),
                Toast.LENGTH_LONG).show();
        getParentFragmentManager().popBackStack();
    }

    /**
     * Locks the account actions while a deletion is in flight, so a second tap
     * cannot start a duplicate destructive call.
     *
     * <p>Only the Delete button: Disable and Re-enable are harmless next to a
     * deletion that is about to remove the account anyway, and leaving them live
     * keeps the screen usable for an administrator who changes their mind.
     */
    private void renderDeleting(boolean deleting) {
        deleteButton.setEnabled(!deleting);
    }

    private void bindCopy(View root, int buttonId, String value, String label) {
        View button = root.findViewById(buttonId);
        if (button == null) return;
        button.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager)
                    requireContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText(label, value));
                Toast.makeText(requireContext(), label + " copied", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Confirms before disabling. The wording states that the reporter cannot
     * undo this by signing in again, which is the one thing an administrator
     * might otherwise assume is the case.
     */
    private void confirmDisable(ResidentAccount resident) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.reporter_disable_account)
                .setMessage(getString(R.string.reporter_disable_confirmation,
                        resident.getFullName()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.reporter_disable_account, (dialog, which) ->
                        viewModel.disableResident(resident))
                .show();
    }

    /**
     * Confirms before restoring. The wording states the retention deadline,
     * because an administrator weighing "disable for a while" against "delete"
     * needs to know when the data goes.
     */
    private void confirmEnable(ResidentAccount resident) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.reporter_enable_account)
                .setMessage(getString(R.string.reporter_enable_confirmation,
                        resident.getFullName()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.reporter_enable_account, (dialog, which) ->
                        viewModel.enableResident(resident))
                .show();
    }

    private String inputValue(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    /**
     * Shows a contact value, or the "not provided" placeholder when the server
     * has none, and returns the raw value for the click handler to use. A row
     * with no value stays tappable so the tap still explains why nothing opens
     * instead of silently doing nothing.
     *
     * @return the trimmed value, or an empty string when there is none
     */
    private String bindContactRow(View row, TextView value, String rawValue, String personName,
                                  int actionDescriptionRes, int emptyMessageRes) {
        boolean present = !isBlank(rawValue);
        String usable = present ? rawValue.trim() : "";
        value.setText(present ? usable : getString(R.string.contact_not_available));
        value.setAlpha(present ? 1f : 0.6f);
        row.setContentDescription(
                getString(present ? actionDescriptionRes : emptyMessageRes) + " " + personName + ".");
        return usable;
    }

    private void renderStatistics(ReportStatistics statistics) {
        if (statistics == null) statistics = new ReportStatistics();
        totalCount.setText(String.valueOf(statistics.getTotal()));
        pendingCount.setText(String.valueOf(statistics.getPending()));
        inProgressCount.setText(String.valueOf(statistics.getInProgress()));
        completedCount.setText(String.valueOf(statistics.getCompleted()));
        deniedCount.setText(String.valueOf(statistics.getDenied()));
    }

    private String getResidentId() {
        return getArguments() == null ? "" : getArguments().getString(ARG_RESIDENT_ID, "");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

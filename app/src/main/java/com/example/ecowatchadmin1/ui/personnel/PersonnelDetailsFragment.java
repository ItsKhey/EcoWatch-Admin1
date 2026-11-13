package com.example.ecowatchadmin1.ui.personnel;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import com.example.ecowatchadmin1.data.models.Personnel;
import com.example.ecowatchadmin1.data.models.PersonnelTaskCounts;
import com.example.ecowatchadmin1.data.models.Report;
import com.example.ecowatchadmin1.ui.reports.ReportDetailDialog;
import com.example.ecowatchadmin1.ui.residents.ReporterReportAdapter;
import com.example.ecowatchadmin1.utils.ContactActions;
import com.example.ecowatchadmin1.utils.PersonnelStatsStore;
import com.example.ecowatchadmin1.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
/**
 * Personnel profile, live monitoring counts and assigned tasks.
 *
 * <p>This screen replaces the former personnel details dialog. It is built to
 * mirror {@code ReporterDetailsFragment} so both detail screens behave the same
 * way, and the account actions the dialog used to carry (edit, reset password,
 * disable) live at the bottom of the page.
 */
public class PersonnelDetailsFragment extends Fragment {
    private static final String ARG_PERSONNEL_ID = "personnel_id";
    private static final long LIVE_REFRESH_MS = 30_000L;

    private PersonnelDetailsViewModel viewModel;
    private SwipeRefreshLayout swipeRefresh;
    private ShapeableImageView avatar;
    private TextView name;
    private TextView personnelId;
    private TextView status;
    private TextView occupationText;
    private TextView descriptionRole;
    private TextView username;
    private TextView email;
    private TextView address;
    private TextView dateCreated;
    private TextView lastActive;
    private TextView lastCollection;
    private TextView contactPhone;
    private TextView contactEmail;
    private TextView contactMessenger;
    private View contactPhoneRow;
    private View contactEmailRow;
    private View contactMessengerRow;
    private TextView totalAssignedCount;
    private TextView onHandCount;
    private TextView inProgressCount;
    private TextView completedCount;
    private View vehicleTitle;
    private View vehicleCard;
    private TextView vehiclePlate;
    private TextView vehicleId;
    private RecyclerView taskRecyclerView;
    private TextView taskEmpty;
    private View loadMoreButton;
    private MaterialButton editButton;
    private MaterialButton resetPasswordButton;
    private MaterialButton disableButton;
    private ReporterReportAdapter taskAdapter;
    private Personnel current;

    /**
     * The raw contact values behind the rows. Held separately from the
     * TextViews because a row with no value renders a "Not provided" placeholder,
     * and passing that placeholder to an intent would build a malformed target.
     */
    private String contactPhoneValue = "";
    private String contactEmailValue = "";
    private String contactMessengerValue = "";

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
    private AlertDialog actionDialog;

    public static PersonnelDetailsFragment newInstance(String personnelId) {
        PersonnelDetailsFragment fragment = new PersonnelDetailsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PERSONNEL_ID, personnelId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_personnel_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        viewModel = new ViewModelProvider(this).get(PersonnelDetailsViewModel.class);

        SessionManager sessionManager = new SessionManager(requireContext());
        String id = getPersonnelId();
        if (!sessionManager.isAdmin() || !sessionManager.isLoggedIn() || isBlank(id)) {
            firstResume = false;
            authorized = false;
            Toast.makeText(requireContext(),
                    "Administrator access is required.", Toast.LENGTH_LONG).show();
            return;
        }
        authorized = true;

        // The counts this device last saw for this cleaner, painted before the
        // observer is registered so the cards are never the layout's placeholder
        // zeros while the statistics request is in flight. The server's answer
        // repaints them and overwrites this.
        renderStats(PersonnelStatsStore.load(requireContext(), getPersonnelId()));

        view.findViewById(R.id.reporterDetailsBack).setOnClickListener(v ->
                getParentFragmentManager().popBackStack());
        setupContactActions();
        setupTasks();
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
            viewModel.load(getPersonnelId(), true);
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

    @Override
    public void onDestroyView() {
        if (actionDialog != null && actionDialog.isShowing()) actionDialog.dismiss();
        actionDialog = null;
        super.onDestroyView();
    }

    private void bindViews(View view) {
        swipeRefresh = view.findViewById(R.id.reporterDetailsSwipeRefresh);
        avatar = view.findViewById(R.id.reporterDetailsAvatar);
        name = view.findViewById(R.id.reporterDetailsName);
        personnelId = view.findViewById(R.id.reporterDetailsResidentId);
        status = view.findViewById(R.id.reporterDetailsStatus);
        occupationText = view.findViewById(R.id.personnelOccupation);
        descriptionRole = view.findViewById(R.id.personnelDesciptionRole);
        username = view.findViewById(R.id.reporterDetailsUsername);
        email = view.findViewById(R.id.reporterDetailsEmail);
        address = view.findViewById(R.id.reporterDetailsAddress);
        dateCreated = view.findViewById(R.id.reporterDetailsDateCreated);
        lastActive = view.findViewById(R.id.reporterDetailsLastActive);
        lastCollection = view.findViewById(R.id.personnelLastCollection);
        contactPhoneRow = view.findViewById(R.id.personnelContactPhoneRow);
        contactEmailRow = view.findViewById(R.id.personnelContactEmailRow);
        contactMessengerRow = view.findViewById(R.id.personnelContactMessengerRow);
        contactPhone = view.findViewById(R.id.personnelPhoneNumber);
        contactEmail = view.findViewById(R.id.personnelDetailsEmail);
        contactMessenger = view.findViewById(R.id.personnelMessengerLink);
        totalAssignedCount = view.findViewById(R.id.totalAssignedReport);
        onHandCount = view.findViewById(R.id.reporterStatOnhand);
        inProgressCount = view.findViewById(R.id.reporterStatInProgress);
        completedCount = view.findViewById(R.id.reporterStatCompleted);
        vehicleTitle = view.findViewById(R.id.personnelDetailsVehicleTitle);
        vehicleCard = view.findViewById(R.id.personnelDetailsVehicleCard);
        vehiclePlate = view.findViewById(R.id.personnelDetailsVehiclePlate);
        vehicleId = view.findViewById(R.id.personnelDetailsVehicleId);
        taskRecyclerView = view.findViewById(R.id.reporterHistoryRecyclerView);
        taskEmpty = view.findViewById(R.id.reporterHistoryEmpty);
        loadMoreButton = view.findViewById(R.id.reporterHistoryLoadMore);
        editButton = view.findViewById(R.id.personnelDetailsEdit);
        resetPasswordButton = view.findViewById(R.id.personnelDetailsResetPassword);
        disableButton = view.findViewById(R.id.personnelDetailsDisable);
    }

    private void setupContactActions() {
        contactPhoneRow.setOnClickListener(v ->
                ContactActions.dial(requireContext(), contactPhoneValue));
        contactEmailRow.setOnClickListener(v ->
                ContactActions.email(requireContext(), contactEmailValue));
        contactMessengerRow.setOnClickListener(v ->
                ContactActions.messenger(requireContext(), contactMessengerValue));
    }

    private void setupTasks() {
        taskAdapter = new ReporterReportAdapter();
        // This screen is the one history that expands each task into its
        // cleaning events; the shared adapter's compact mode stays the default.
        taskAdapter.setShowTimeline(true);
        taskRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        taskRecyclerView.setAdapter(taskAdapter);
        taskAdapter.setListener(report -> ReportDetailDialog.showReadOnly(this, report));
        loadMoreButton.setOnClickListener(v -> viewModel.loadMoreReports());
    }

    private void setupActions() {
        editButton.setOnClickListener(v -> {
            if (current != null) showEditDialog(current);
        });
        resetPasswordButton.setOnClickListener(v -> {
            if (current != null) showResetDialog(current);
        });
        disableButton.setOnClickListener(v -> {
            if (current != null) confirmDisable(current);
        });
    }

    private void setupRefresh() {
        swipeRefresh.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.green));
        swipeRefresh.setOnRefreshListener(() -> viewModel.refresh());
    }

    private void observeViewModel() {
        viewModel.getPersonnel().observe(getViewLifecycleOwner(), this::renderPersonnel);
        viewModel.getTaskCounts().observe(getViewLifecycleOwner(), this::renderTaskCounts);
        viewModel.getReports().observe(getViewLifecycleOwner(), reports -> {
            java.util.List<Report> values = reports == null
                    ? new java.util.ArrayList<>() : reports;
            if (current != null && !isBlank(current.getTeamName())) {
                for (Report report : values) {
                    if (report != null && report.requiresTeam() && isBlank(report.getAssignedTeamLabel())) {
                        report.setAssignedTeamName(current.getTeamName());
                    }
                }
            }
            taskAdapter.setReports(values);
            taskEmpty.setVisibility(values.isEmpty() ? View.VISIBLE : View.GONE);
            if (current != null) bindLastCollection(current);
        });
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            if (swipeRefresh != null) swipeRefresh.setRefreshing(Boolean.TRUE.equals(loading));
        });
        viewModel.getLoadingReports().observe(getViewLifecycleOwner(), loading -> {
            if (loadMoreButton != null) loadMoreButton.setEnabled(!Boolean.TRUE.equals(loading));
        });
        viewModel.getHasMoreReports().observe(getViewLifecycleOwner(), hasMore -> {
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
        viewModel.getMessage().observe(getViewLifecycleOwner(), message -> {
            if (message == null || message.trim().isEmpty()) return;
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            viewModel.clearMessage();
        });
        viewModel.getResetPassword().observe(getViewLifecycleOwner(), temporaryPassword -> {
            if (temporaryPassword == null || temporaryPassword.trim().isEmpty()) return;
            showPasswordHandoff(current, temporaryPassword);
            viewModel.clearResetPassword();
        });
    }

    private void renderPersonnel(Personnel person) {
        if (person == null) return;
        current = person;
        name.setText(person.getFullName());
        personnelId.setText(PersonnelUi.valueOrDash(person.getPersonnelId()));
        PersonnelUi.bindStatus(status, person);
        if (occupationText != null) {
            if (person.isDriver()) {
                occupationText.setText(R.string.personnel_role_driver);
            } else if (person.getOccupation() != null && !person.getOccupation().trim().isEmpty()) {
                occupationText.setText(person.getOccupation());
            } else {
                occupationText.setText(R.string.personnel_role_regular);
            }
        }
        bindDescriptionRole(person);
        PersonnelUi.loadAvatar(avatar, person.getProfileImageUrl());
        username.setText(PersonnelUi.valueOrDash(person.getUsername()));
        email.setText(PersonnelUi.valueOrDash(person.getGmail()));
        address.setText(PersonnelUi.valueOrDash(person.getAddress()));
        dateCreated.setText(PersonnelUi.formatDate(person.getDateCreated()));
        lastActive.setText(PersonnelUi.formatLastActive(person.getLastActive()));
        bindLastCollection(person);

        String personName = person.getFullName();
        contactPhoneValue = bindContactRow(contactPhoneRow, contactPhone,
                person.getPhoneNumber(), personName,
                R.string.contact_phone_description, R.string.contact_no_phone_number);
        contactEmailValue = bindContactRow(contactEmailRow, contactEmail,
                person.getGmail(), personName,
                R.string.contact_email_description, R.string.contact_no_email_address);
        contactMessengerValue = bindContactRow(contactMessengerRow, contactMessenger,
                person.getMessengerLink(), personName,
                R.string.contact_messenger_description, R.string.contact_no_messenger_link);

        // A disabled account cannot be edited or re-enabled from this screen, so
        // only the disable action is meaningful for it.
        boolean enabled = person.isEnabled();
        editButton.setEnabled(enabled);
        resetPasswordButton.setEnabled(enabled);
        disableButton.setEnabled(enabled);

        bindAssignedVehicle(person);

        if (taskAdapter != null && !isBlank(person.getTeamName())) {
            java.util.List<Report> currentReports = viewModel.getReports().getValue();
            if (currentReports != null && !currentReports.isEmpty()) {
                boolean changed = false;
                for (Report report : currentReports) {
                    if (report != null && report.requiresTeam() && isBlank(report.getAssignedTeamLabel())) {
                        report.setAssignedTeamName(person.getTeamName());
                        changed = true;
                    }
                }
                if (changed) taskAdapter.notifyDataSetChanged();
            }
        }
    }

    /**
     * Paints the last finished collection for this person.
     *
     * <p>An explicit server stamp wins. Otherwise the newest completed task in
     * the loaded history is used, which keeps older deployments useful when
     * they do not send the explicit field. The account's last activity is
     * deliberately not a fallback: signing in is not a collection.
     */
    private void bindLastCollection(Personnel person) {
        if (lastCollection == null) return;
        String raw = person == null ? null : person.getLastCollectionAt();
        if (isBlank(raw) && viewModel != null) {
            raw = PersonnelUi.latestCollectionTimestamp(viewModel.getReports().getValue());
        }
        lastCollection.setText(PersonnelUi.formatCollectionDateTime(raw));
    }

    /** Shows "Leader/Member/Driver of Team X", hiding the line when unassigned. */
    private void bindDescriptionRole(Personnel person) {
        if (descriptionRole == null) return;
        String teamName = person.getTeamName();
        if (teamName == null || teamName.trim().isEmpty()) {
            descriptionRole.setVisibility(View.GONE);
            return;
        }

        String role;
        if (person.isDriver()) {
            role = getString(R.string.personnel_role_driver);
        } else if ("Leader".equalsIgnoreCase(person.getTeamRole())) {
            role = getString(R.string.personnel_role_leader);
        } else {
            role = getString(R.string.personnel_role_member);
        }
        descriptionRole.setText(getString(R.string.personnel_role_of_team, role, teamName.trim()));
        descriptionRole.setVisibility(View.VISIBLE);
    }

    /**
     * Shows the truck this driver is assigned to.
     *
     * <p>Heading and card move together: a cleaner never has a truck, and a
     * "Vehicle Assigned" label above an empty gap would claim otherwise. A driver
     * with no truck yet is also left out, for the same reason the list card hides
     * it — there is nothing there to name.
     */
    private void bindAssignedVehicle(Personnel person) {
        boolean hasVehicle = person.isDriver() && person.hasAssignedVehicle();
        vehicleTitle.setVisibility(hasVehicle ? View.VISIBLE : View.GONE);
        vehicleCard.setVisibility(hasVehicle ? View.VISIBLE : View.GONE);
        if (!hasVehicle) return;

        vehiclePlate.setText(PersonnelUi.valueOrDash(person.getAssignedVehiclePlateNumber()));
        vehicleId.setText(getString(R.string.personnel_vehicle_id_format,
                person.getAssignedVehicleId()));
    }

    /**
     * Paints the assignment-derived counts and records them as this personnel's
     * last known values.
     *
     * <p>These come from the cleaner's assignment rows, which is the same source
     * the Personnel app counts, so both screens show the same three numbers.
     * The report-status aggregates cannot stand in: a report only becomes
     * "completed" when an administrator approves it, so a cleaner with ten
     * finished tasks read zero until all ten reports were signed off.
     *
     * <p>A null means the endpoint has not answered yet (still in flight, or it
     * failed), not that the counts are zero: the cards keep whatever was saved
     * for this cleaner instead of dropping to the placeholder zeros.
     */
    private void renderTaskCounts(PersonnelTaskCounts counts) {
        if (counts == null) return;
        PersonnelStatsStore.Stats stats = new PersonnelStatsStore.Stats(
                counts.getTotalAssigned(), counts.getOnHand(),
                counts.getInProgress(), counts.getCompleted());
        renderStats(stats);
        PersonnelStatsStore.save(requireContext(), getPersonnelId(), stats);
    }

    /** Writes the four totals into their cards, without touching the labels. */
    private void renderStats(PersonnelStatsStore.Stats stats) {
        if (stats == null) stats = PersonnelStatsStore.Stats.empty();
        if (totalAssignedCount == null || onHandCount == null
                || inProgressCount == null || completedCount == null) return;
        totalAssignedCount.setText(String.valueOf(stats.totalAssigned));
        onHandCount.setText(String.valueOf(stats.onHand));
        inProgressCount.setText(String.valueOf(stats.inProgress));
        completedCount.setText(String.valueOf(stats.completed));
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

    private void showEditDialog(Personnel person) {
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_personnel_edit, null, false);
        TextInputLayout firstLayout =
                content.findViewById(R.id.editFirstNameLayout);
        TextInputLayout lastLayout =
                content.findViewById(R.id.editLastNameLayout);
        TextInputLayout addressLayout =
                content.findViewById(R.id.editAddressLayout);
        TextInputLayout gmailLayout =
                content.findViewById(R.id.editGmailLayout);
        TextInputLayout usernameLayout =
                content.findViewById(R.id.editUsernameLayout);
        TextInputLayout phoneLayout =
                content.findViewById(R.id.editPhoneNumberLayout);
        TextInputLayout messengerLayout =
                content.findViewById(R.id.editMessengerLinkLayout);
        TextInputEditText first =
                content.findViewById(R.id.editFirstName);
        TextInputEditText last =
                content.findViewById(R.id.editLastName);
        TextInputEditText address =
                content.findViewById(R.id.editAddress);
        TextInputEditText gmail =
                content.findViewById(R.id.editGmail);
        TextInputEditText username =
                content.findViewById(R.id.editUsername);
        TextInputEditText phone =
                content.findViewById(R.id.editPhoneNumber);
        TextInputEditText messenger =
                content.findViewById(R.id.editMessengerLink);
        first.setText(person.getFirstName());
        last.setText(person.getLastName());
        address.setText(person.getAddress());
        gmail.setText(person.getGmail());
        username.setText(person.getUsername());
        phone.setText(person.getPhoneNumber());
        messenger.setText(person.getMessengerLink());

        actionDialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.personnel_save_changes, null)
                .create();
        actionDialog.setOnShowListener(dialog -> actionDialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    firstLayout.setError(null);
                    lastLayout.setError(null);
                    addressLayout.setError(null);
                    gmailLayout.setError(null);
                    usernameLayout.setError(null);
                    phoneLayout.setError(null);
                    messengerLayout.setError(null);

                    String firstValue = value(first);
                    String lastValue = value(last);
                    String addressValue = value(address);
                    String gmailValue = value(gmail).toLowerCase(java.util.Locale.US);
                    String usernameValue = value(username);
                    String phoneValue = value(phone);
                    String messengerValue = value(messenger);

                    boolean valid = true;
                    valid &= requireField(firstLayout, firstValue);
                    valid &= requireField(lastLayout, lastValue);
                    valid &= requireField(addressLayout, addressValue);
                    if (!gmailValue.matches("^[A-Za-z0-9._%+\\-]+@gmail\\.com$")) {
                        gmailLayout.setError(getString(R.string.personnel_error_gmail));
                        valid = false;
                    }
                    if (!isValidPersonnelUsername(usernameValue)) {
                        usernameLayout.setError(getString(R.string.personnel_error_username));
                        valid = false;
                    }
                    if (!isValidPhoneNumber(phoneValue)) {
                        phoneLayout.setError(getString(R.string.personnel_details_error_phone));
                        valid = false;
                    }
                    if (!isValidMessengerLink(messengerValue)) {
                        messengerLayout.setError(
                                getString(R.string.personnel_details_error_messenger_link));
                        valid = false;
                    }
                    if (!valid) return;

                    viewModel.updatePersonnel(person, firstValue, lastValue, addressValue,
                            gmailValue, usernameValue, phoneValue, messengerValue);
                    actionDialog.dismiss();
                }));
        actionDialog.show();
    }

    private void showResetDialog(Personnel person) {
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_personnel_reset_password, null, false);
        TextInputLayout passwordLayout =
                content.findViewById(R.id.resetPasswordLayout);
        TextInputLayout confirmLayout =
                content.findViewById(R.id.resetConfirmPasswordLayout);
        TextInputEditText password =
                content.findViewById(R.id.resetPassword);
        TextInputEditText confirm =
                content.findViewById(R.id.resetConfirmPassword);
        TextView label = content.findViewById(R.id.resetPersonnelLabel);
        label.setText(getString(R.string.personnel_id_label) + ": "
                + PersonnelUi.valueOrDash(person.getPersonnelId()) + "\n" + person.getFullName());

        actionDialog = new MaterialAlertDialogBuilder(requireContext())
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.personnel_reset, null)
                .create();
        actionDialog.setOnShowListener(dialog -> actionDialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    passwordLayout.setError(null);
                    confirmLayout.setError(null);
                    String passwordValue = value(password);
                    String confirmValue = value(confirm);
                    if (passwordValue.length() < 8) {
                        passwordLayout.setError(getString(R.string.personnel_error_password));
                        return;
                    }
                    if (!passwordValue.equals(confirmValue)) {
                        confirmLayout.setError(getString(R.string.personnel_error_confirm));
                        return;
                    }
                    // The server hands the temporary password back so the admin can
                    // pass it on. It has to be shown in a copyable dialog rather than
                    // a toast, otherwise the admin cannot relay it.
                    viewModel.resetPassword(person, passwordValue);
                    actionDialog.dismiss();
                }));
        actionDialog.show();
    }

    /** Shown once a reset succeeds, so the temporary password can be copied. */
    private void showPasswordHandoff(Personnel person, String temporaryPassword) {
        if (!isAdded() || isBlank(temporaryPassword)) return;
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_personnel_credentials, null, false);
        TextView id = content.findViewById(R.id.credentialPersonnelId);
        TextView name = content.findViewById(R.id.credentialName);
        TextView address = content.findViewById(R.id.credentialAddress);
        TextView gmail = content.findViewById(R.id.credentialGmail);
        TextView username = content.findViewById(R.id.credentialUsername);
        TextView passwordText = content.findViewById(R.id.credentialPassword);
        id.setText(PersonnelUi.valueOrDash(person.getPersonnelId()));
        name.setText(person.getFullName());
        address.setText(PersonnelUi.valueOrDash(person.getAddress()));
        gmail.setText(PersonnelUi.valueOrDash(person.getGmail()));
        username.setText(PersonnelUi.valueOrDash(person.getUsername()));
        passwordText.setText(temporaryPassword);

        actionDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.personnel_details_reset_password)
                .setView(content)
                .setPositiveButton(R.string.personnel_done, null)
                .setCancelable(false)
                .create();
        actionDialog.setCanceledOnTouchOutside(false);
        bindCopy(content, R.id.copyPersonnelId, id.getText().toString(),
                getString(R.string.personnel_id_label));
        bindCopy(content, R.id.copyName, name.getText().toString(),
                getString(R.string.personnel_name_label));
        bindCopy(content, R.id.copyAddress, address.getText().toString(),
                getString(R.string.personnel_address));
        bindCopy(content, R.id.copyGmail, gmail.getText().toString(),
                getString(R.string.personnel_gmail_label));
        bindCopy(content, R.id.copyUsername, username.getText().toString(),
                getString(R.string.personnel_username_label));
        bindCopy(content, R.id.copyPassword, passwordText.getText().toString(),
                getString(R.string.personnel_password));
        actionDialog.show();
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

    private void confirmDisable(Personnel person) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.personnel_disable_account)
                .setMessage(getString(R.string.personnel_disable_confirmation,
                        person.getFullName()))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.personnel_disable_account, (dialog, which) ->
                        viewModel.disablePersonnel(person))
                .show();
    }

    private boolean requireField(TextInputLayout layout,
                                String value) {
        if (isBlank(value)) {
            layout.setError(getString(R.string.personnel_error_required));
            return false;
        }
        return true;
    }

    private boolean isValidPersonnelUsername(String value) {
        return value != null && value.matches("^[a-z0-9][a-z0-9._-]{2,49}$");
    }

    /** Mirrors the backend phone constraint so the error shows before the round trip. */
    private boolean isValidPhoneNumber(String value) {
        return value == null || value.isEmpty() || value.matches("^[0-9+() .-]{7,25}$");
    }

    private boolean isValidMessengerLink(String value) {
        return value == null || value.isEmpty()
                || value.matches("^https?://\\S+$");
    }

    private String value(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private String getPersonnelId() {
        return getArguments() == null ? "" : getArguments().getString(ARG_PERSONNEL_ID, "");
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

package com.example.ecowatchadmin1.ui.announcements;

import android.os.Bundle;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.ecowatchadmin1.R;
import com.example.ecowatchadmin1.data.models.Announcement;
import com.example.ecowatchadmin1.data.repository.AnnouncementRepository;
import com.example.ecowatchadmin1.utils.LocaleHelper;
import com.example.ecowatchadmin1.utils.SessionManager;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Compose and publish a CENRO announcement.
 *
 * <p>Every send goes to the real backend. The screen only reports success after
 * the server has accepted the announcement, and it stays on screen with the
 * form intact when the request fails.
 */
public class CreateAnnouncementActivity extends AppCompatActivity
        implements AnnouncementRepository.AnnouncementCallback {

    private TextInputLayout titleLayout;
    private TextInputLayout messageLayout;
    private TextInputEditText titleInput;
    private TextInputEditText messageInput;
    private RadioGroup audienceGroup;
    private RadioGroup priorityGroup;
    private RadioGroup expiryGroup;
    private MaterialButton sendButton;
    private LinearProgressIndicator progressIndicator;

    private AnnouncementRepository repository;

    /** Guards against a double tap queueing two announcements. */
    private boolean sending;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LocaleHelper.applyLocale(this, new SessionManager(this).getLanguage());
        androidx.activity.EdgeToEdge.enable(this);
        setContentView(R.layout.activity_create_announcement);

        View toolbar = findViewById(R.id.announcementBackBtn);
        if (toolbar != null) toolbar.setOnClickListener(v -> finish());

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.announcementSwipeRefresh),
                (v, insets) -> {
                    Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                    v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
                    return insets;
                });

        titleLayout = findViewById(R.id.announcementTitleLayout);
        messageLayout = findViewById(R.id.announcementMessageLayout);
        titleInput = findViewById(R.id.announcementTitleInput);
        messageInput = findViewById(R.id.announcementMessageInput);
        audienceGroup = findViewById(R.id.announcementAudienceGroup);
        priorityGroup = findViewById(R.id.announcementPriorityGroup);
        expiryGroup = findViewById(R.id.announcementExpiryGroup);
        sendButton = findViewById(R.id.sendAnnouncementBtn);
        progressIndicator = findViewById(R.id.announcementProgress);

        repository = new AnnouncementRepository(getApplication());

        // The note explains a permanent, unrecoverable delete, so it stays
        // visible for every choice rather than only surprising the default.
        expiryGroup.setOnCheckedChangeListener((group, checkedId) -> updateExpiryNote(checkedId));

        sendButton.setOnClickListener(v -> submit());
    }

    /**
     * Reflects the chosen window back to the administrator.
     *
     * <p>"Never expires" is spelled out separately because it is the one choice
     * that leaves a row in the database indefinitely.
     */
    private void updateExpiryNote(int checkedId) {
        TextView expiryNote = findViewById(R.id.expiryNote);
        if (expiryNote == null) return;

        if (checkedId == R.id.expiryNever) {
            expiryNote.setText(R.string.expiry_note_never);
            return;
        }
        expiryNote.setText(R.string.expiry_note);
    }

    private void submit() {
        if (sending) return;

        String title = titleInput.getText() == null ? "" : titleInput.getText().toString().trim();
        String message = messageInput.getText() == null ? "" : messageInput.getText().toString().trim();

        // Clear any previous inline error so a retry does not keep stale text.
        titleLayout.setError(null);
        messageLayout.setError(null);

        if (title.isEmpty()) {
            titleLayout.setError(getString(R.string.announcement_title_required));
            titleInput.requestFocus();
            return;
        }
        if (message.isEmpty()) {
            messageLayout.setError(getString(R.string.announcement_message_required));
            messageInput.requestFocus();
            return;
        }

        setSending(true);
        repository.publish(title, message, selectedAudience(), selectedPriority(),
                selectedExpiryDays(), this);
    }

    private String selectedAudience() {
        int checked = audienceGroup.getCheckedRadioButtonId();
        if (checked == R.id.audienceBarangay) {
            return getString(R.string.audience_specific_barangay);
        }
        if (checked == R.id.audienceSelectedResidents) {
            return getString(R.string.audience_selected_residents);
        }
        return AnnouncementRepository.AUDIENCE_ALL_RESIDENTS;
    }

    private String selectedPriority() {
        int checked = priorityGroup.getCheckedRadioButtonId();
        return checked == R.id.priorityImportant
                ? AnnouncementRepository.PRIORITY_IMPORTANT
                : AnnouncementRepository.PRIORITY_NORMAL;
    }

    /**
     * Maps the chosen radio button to its window in days.
     *
     * <p>Falls back to the 7-day default if the group somehow reports nothing
     * checked, so a missing selection can never publish a permanent
     * announcement by accident.
     */
    private int selectedExpiryDays() {
        int checked = expiryGroup.getCheckedRadioButtonId();

        if (checked == R.id.expiry1Day) return AnnouncementRepository.EXPIRY_1_DAY;
        if (checked == R.id.expiry3Days) return AnnouncementRepository.EXPIRY_3_DAYS;
        if (checked == R.id.expiry14Days) return AnnouncementRepository.EXPIRY_14_DAYS;
        if (checked == R.id.expiry30Days) return AnnouncementRepository.EXPIRY_30_DAYS;
        if (checked == R.id.expiryNever) return AnnouncementRepository.EXPIRY_NEVER;

        return AnnouncementRepository.EXPIRY_DEFAULT_DAYS;
    }

    private void setSending(boolean inFlight) {
        sending = inFlight;
        if (sendButton != null) {
            sendButton.setEnabled(!inFlight);
            sendButton.setText(inFlight
                    ? R.string.sending_announcement
                    : R.string.send_announcement);
        }
        if (progressIndicator != null) {
            progressIndicator.setVisibility(inFlight ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onSuccess(@NonNull Announcement announcement) {
        if (isFinishing() || isDestroyed()) return;
        setSending(false);
        Toast.makeText(this, R.string.announcement_sent, Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    public void onError(@NonNull String error) {
        if (isFinishing() || isDestroyed()) return;
        setSending(false);
        // The form is left untouched so the administrator can correct and resend.
        Toast.makeText(this, error, Toast.LENGTH_LONG).show();
    }
}

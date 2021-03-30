package de.culture4life.luca.ui.meeting;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.ncorti.slidetoact.SlideToActView;

import de.culture4life.luca.R;
import de.culture4life.luca.ui.BaseFragment;
import de.culture4life.luca.ui.dialog.BaseDialogFragment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.reactivex.rxjava3.core.Completable;

public class MeetingFragment extends BaseFragment<MeetingViewModel> {

    private ImageView qrCodeImageView;
    private View loadingView;
    private TextView durationTextView;
    private TextView membersTextView;
    private ImageView meetingDescriptionInfoImageView;
    private ImageView meetingMembersInfoImageView;
    private SlideToActView slideToActView;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        qrCodeImageView = view.findViewById(R.id.qrCodeImageView);
        loadingView = view.findViewById(R.id.loadingLayout);
        durationTextView = view.findViewById(R.id.durationTextView);
        membersTextView = view.findViewById(R.id.membersTextView);
        meetingDescriptionInfoImageView = view.findViewById(R.id.infoImageView);
        meetingMembersInfoImageView = view.findViewById(R.id.meetingMembersInfoImageView);
        slideToActView = view.findViewById(R.id.slideToActView);

        return view;
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.fragment_meeting;
    }

    @Override
    protected Class<MeetingViewModel> getViewModelClass() {
        return MeetingViewModel.class;
    }

    @Override
    protected Completable initializeViews() {
        return super.initializeViews()
                .andThen(Completable.fromAction(() -> {
                    observe(viewModel.getIsHostingMeeting(), isHostingMeeting -> {
                        if (!isHostingMeeting) {
                            navigationController.navigate(R.id.action_meetingFragment_to_qrCodeFragment);
                        }
                    });
                    observe(viewModel.getQrCode(), value -> qrCodeImageView.setImageBitmap(value));
                    observe(viewModel.getIsLoading(), loading -> loadingView.setVisibility(loading ? View.VISIBLE : View.GONE));
                    observe(viewModel.getDuration(), value -> durationTextView.setText(value));
                    observe(viewModel.getMembersCount(), value -> membersTextView.setText(value));
                    meetingDescriptionInfoImageView.setOnClickListener(v -> showMeetingDescriptionInfo());
                    meetingMembersInfoImageView.setOnClickListener(v -> showMeetingMembersInfo());
                    slideToActView.setOnSlideCompleteListener(view -> viewModel.onMeetingEndRequested());
                    slideToActView.setReversed(true);

                    observe(viewModel.getIsLoading(), loading -> {
                        if (!loading) {
                            slideToActView.resetSlider();
                        }
                    });

                }));
    }

    private void showEndMeetingConfirmationDialog() {
        new BaseDialogFragment(new MaterialAlertDialogBuilder(getActivity())
                .setTitle(R.string.meeting_end_confirmation_heading)
                .setMessage(R.string.meeting_end_confirmation_description)
                .setPositiveButton(R.string.action_ok, (dialog, which) -> viewModel.onMeetingEndRequested())
                .setNegativeButton(R.string.action_cancel, (dialog, which) -> {
                    // do nothing
                })).show();
    }

    private void showMeetingDescriptionInfo() {
        new BaseDialogFragment(new MaterialAlertDialogBuilder(getActivity())
                .setTitle(R.string.meeting_heading)
                .setMessage(R.string.meeting_description_info)
                .setPositiveButton(R.string.action_ok, (dialog, which) -> {
                    // do nothing
                })).show();
    }

    private void showMeetingMembersInfo() {
        String count = viewModel.getMembersCount().getValue();
        String checkedIn = viewModel.getCheckedInMemberNames().getValue();
        String checkedOut = viewModel.getCheckedOutMemberNames().getValue();
        new BaseDialogFragment(new MaterialAlertDialogBuilder(getActivity())
                .setTitle(R.string.meeting_members_heading)
                .setMessage(getString(R.string.meeting_members_info, count, checkedIn, checkedOut))
                .setPositiveButton(R.string.action_ok, (dialog, which) -> {
                    // do nothing
                })).show();
    }

}

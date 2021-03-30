package de.culture4life.luca.ui.accesseddata;

import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import de.culture4life.luca.R;
import de.culture4life.luca.ui.BaseFragment;
import de.culture4life.luca.ui.UiUtil;

import io.reactivex.rxjava3.core.Completable;

public class AccessedDataFragment extends BaseFragment<AccessedDataViewModel> {

    private TextView emptyTitleTextView;
    private TextView emptyDescriptionTextView;
    private ImageView emptyImageView;
    private ListView accessedDataListView;
    private AccessedDataListAdapter accessedDataListAdapter;

    @Override
    protected int getLayoutResource() {
        return R.layout.fragment_accessed_data;
    }

    @Override
    protected Class<AccessedDataViewModel> getViewModelClass() {
        return AccessedDataViewModel.class;
    }

    @Override
    protected Completable initializeViews() {
        return super.initializeViews()
                .andThen(Completable.fromAction(() -> {
                    initializeAccessedDataItemsViews();
                    initializeEmptyStateViews();
                }));
    }

    private void initializeAccessedDataItemsViews() {
        accessedDataListView = getView().findViewById(R.id.accessedDataListView);
        View paddingView = new View(getContext());
        paddingView.setMinimumHeight((int) UiUtil.convertDpToPixel(16, getContext()));
        accessedDataListView.addHeaderView(paddingView);

        accessedDataListAdapter = new AccessedDataListAdapter(getContext(), accessedDataListView.getId());
        accessedDataListView.setAdapter(accessedDataListAdapter);

        observe(viewModel.getAccessedDataItems(), items -> accessedDataListAdapter.setHistoryItems(items));
    }

    private void initializeEmptyStateViews() {
        emptyTitleTextView = getView().findViewById(R.id.emptyTitleTextView);
        emptyDescriptionTextView = getView().findViewById(R.id.emptyDescriptionTextView);
        emptyImageView = getView().findViewById(R.id.emptyImageView);

        observe(viewModel.getAccessedDataItems(), items -> {
            int emptyStateVisibility = items.isEmpty() ? View.VISIBLE : View.GONE;
            int contentVisibility = !items.isEmpty() ? View.VISIBLE : View.GONE;
            emptyTitleTextView.setVisibility(emptyStateVisibility);
            emptyDescriptionTextView.setVisibility(emptyStateVisibility);
            emptyImageView.setVisibility(emptyStateVisibility);
            accessedDataListView.setVisibility(contentVisibility);
        });
    }

}

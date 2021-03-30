package de.culture4life.luca.ui.history;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import de.culture4life.luca.R;

import java.util.List;

import androidx.annotation.NonNull;
import timber.log.Timber;

public class HistoryListAdapter extends ArrayAdapter<HistoryListItem> {

    private ItemClickHandler itemClickHandler;

    public HistoryListAdapter(@NonNull Context context, int resource) {
        super(context, resource);
    }

    public void setItemClickHandler(ItemClickHandler itemClickHandler) {
        this.itemClickHandler = itemClickHandler;
    }

    public void setHistoryItems(@NonNull List<HistoryListItem> items) {
        if (shouldUpdateDataSet(items)) {
            clear();
            addAll(items);
            notifyDataSetChanged();
        }
    }

    private boolean shouldUpdateDataSet(@NonNull List<HistoryListItem> items) {
        if (items.size() != getCount()) {
            return true;
        }
        for (int itemIndex = 0; itemIndex < getCount(); itemIndex++) {
            if (!items.contains(getItem(itemIndex))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup container) {
        if (convertView == null) {
            LayoutInflater layoutInflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = layoutInflater.inflate(R.layout.history_list_item, container, false);
        }

        HistoryListItem item = getItem(position);

        View topLineView = convertView.findViewById(R.id.topLineView);
        View bottomLineView = convertView.findViewById(R.id.bottomLineView);
        TextView titleTextView = convertView.findViewById(R.id.itemTitleTextView);
        TextView descriptionTextView = convertView.findViewById(R.id.itemDescriptionTextView);
        ImageView titleImageView = convertView.findViewById(R.id.itemTitleImageView);
        ImageView descriptionImageView = convertView.findViewById(R.id.itemDescriptionImageView);
        TextView timeTextView = convertView.findViewById(R.id.itemTimeTextView);

        topLineView.setVisibility(position > 0 ? View.VISIBLE : View.GONE);
        bottomLineView.setVisibility(position < getCount() - 1 ? View.VISIBLE : View.GONE);
        titleTextView.setText(item.getTitle());
        descriptionTextView.setText(item.getDescription());
        descriptionTextView.setVisibility(item.getDescription() != null ? View.VISIBLE : View.GONE);

        if (item.getAdditionalDetails() != null) {
            titleImageView.setImageResource(item.getIconResourceId());
            titleImageView.setVisibility(View.VISIBLE);
            titleImageView.setOnClickListener(v -> showAdditionalDetails(item));
            titleTextView.setOnClickListener(v -> showAdditionalDetails(item));
        } else {
            titleImageView.setVisibility(View.GONE);
            titleImageView.setOnClickListener(null);
            descriptionImageView.setVisibility(View.GONE);
            descriptionImageView.setOnClickListener(null);
            descriptionTextView.setOnClickListener(null);
            titleTextView.setOnClickListener(null);
        }

        timeTextView.setText(item.getTime());

        return convertView;
    }

    private void showAdditionalDetails(@NonNull HistoryListItem item) {
        if (itemClickHandler == null) {
            Timber.w("No item click handler available for %s", item);
            return;
        }
        itemClickHandler.showAdditionalDetails(item);
    }

    public interface ItemClickHandler {

        void showAdditionalDetails(@NonNull HistoryListItem item);

    }

}

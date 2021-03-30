package de.culture4life.luca.ui.accesseddata;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import de.culture4life.luca.R;

import java.util.List;

import androidx.annotation.NonNull;

public class AccessedDataListAdapter extends ArrayAdapter<AccessedDataListItem> {

    public AccessedDataListAdapter(@NonNull Context context, int resource) {
        super(context, resource);
    }

    public void setHistoryItems(@NonNull List<AccessedDataListItem> items) {
        if (shouldUpdateDataSet(items)) {
            clear();
            addAll(items);
            notifyDataSetChanged();
        }
    }

    private boolean shouldUpdateDataSet(@NonNull List<AccessedDataListItem> items) {
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
            convertView = layoutInflater.inflate(R.layout.accessed_data_list_item, container, false);
        }

        AccessedDataListItem item = getItem(position);

        TextView titleTextView = convertView.findViewById(R.id.itemTitleTextView);
        TextView descriptionTextView = convertView.findViewById(R.id.itemDescriptionTextView);
        TextView timeTextView = convertView.findViewById(R.id.itemTimeTextView);

        titleTextView.setText(item.getTitle());
        descriptionTextView.setText(item.getDescription());
        timeTextView.setText(item.getTime());

        return convertView;
    }

}

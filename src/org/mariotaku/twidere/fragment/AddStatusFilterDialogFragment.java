package org.mariotaku.twidere.fragment;

import static org.mariotaku.twidere.util.Utils.getDisplayName;
import static org.mariotaku.twidere.util.Utils.makeFilterdUserContentValues;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;

import com.twitter.Extractor;

import org.mariotaku.twidere.R;
import org.mariotaku.twidere.model.ParcelableStatus;
import org.mariotaku.twidere.model.ParcelableStatus.ParcelableUserMention;
import org.mariotaku.twidere.provider.TweetStore.Filters;
import org.mariotaku.twidere.util.HtmlEscapeHelper;
import org.mariotaku.twidere.util.ParseUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class AddStatusFilterDialogFragment extends BaseSupportDialogFragment implements OnMultiChoiceClickListener,
		OnClickListener {

	public static final String FRAGMENT_TAG = "add_status_filter";

	private final Extractor mExtractor = new Extractor();
	private FilterItemInfo[] mFilterItems;
	private final Set<FilterItemInfo> mCheckedFilterItems = new HashSet<FilterItemInfo>();

	@Override
	public void onClick(final DialogInterface dialog, final int which) {
		final ArrayList<ContentValues> users = new ArrayList<ContentValues>();
		final ArrayList<ContentValues> keywords = new ArrayList<ContentValues>();
		final ArrayList<ContentValues> sources = new ArrayList<ContentValues>();
		for (final FilterItemInfo info : mCheckedFilterItems) {
			final Object value = info.value;
			if (value instanceof ParcelableUserMention) {
				final ContentValues values = makeFilterdUserContentValues((ParcelableUserMention) value);
				users.add(values);
			} else if (value instanceof ParcelableStatus) {
				final ContentValues values = makeFilterdUserContentValues((ParcelableStatus) value);
				users.add(values);
			} else if (info.type == FilterItemInfo.FILTER_TYPE_KEYWORD) {
				if (value != null) {
					final ContentValues values = new ContentValues();
					values.put(Filters.Keywords.VALUE, "#" + ParseUtils.parseString(value));
					keywords.add(values);
				}
			} else if (info.type == FilterItemInfo.FILTER_TYPE_SOURCE) {
				if (value != null) {
					final ContentValues values = new ContentValues();
					values.put(Filters.Sources.VALUE, ParseUtils.parseString(value));
					sources.add(values);
				}
			}
		}
		final ContentResolver resolver = getContentResolver();
		resolver.bulkInsert(Filters.Users.CONTENT_URI, users.toArray(new ContentValues[users.size()]));
		resolver.bulkInsert(Filters.Keywords.CONTENT_URI, keywords.toArray(new ContentValues[keywords.size()]));
		resolver.bulkInsert(Filters.Sources.CONTENT_URI, sources.toArray(new ContentValues[sources.size()]));
	}

	@Override
	public void onClick(final DialogInterface dialog, final int which, final boolean isChecked) {
		if (isChecked) {
			mCheckedFilterItems.add(mFilterItems[which]);
		} else {
			mCheckedFilterItems.remove(mFilterItems[which]);
		}
	}

	@Override
	public Dialog onCreateDialog(final Bundle savedInstanceState) {
		final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		mFilterItems = getFilterItemsInfo();
		final String[] entries = new String[mFilterItems.length];
		for (int i = 0, j = entries.length; i < j; i++) {
			final FilterItemInfo info = mFilterItems[i];
			switch (info.type) {
				case FilterItemInfo.FILTER_TYPE_USER:
					entries[i] = getString(R.string.user_filter_name, getName(info.value));
					break;
				case FilterItemInfo.FILTER_TYPE_KEYWORD:
					entries[i] = getString(R.string.keyword_filter_name, getName(info.value));
					break;
				case FilterItemInfo.FILTER_TYPE_SOURCE:
					entries[i] = getString(R.string.source_filter_name, getName(info.value));
					break;
			}
		}
		builder.setTitle(R.string.add_to_filter);
		builder.setMultiChoiceItems(entries, null, this);
		builder.setPositiveButton(android.R.string.ok, this);
		builder.setNegativeButton(android.R.string.cancel, null);
		return builder.create();
	}

	private FilterItemInfo[] getFilterItemsInfo() {
		final Bundle args = getArguments();
		if (args == null || !args.containsKey(EXTRA_STATUS)) return new FilterItemInfo[0];
		final ParcelableStatus status = args.getParcelable(EXTRA_STATUS);
		final ArrayList<FilterItemInfo> list = new ArrayList<FilterItemInfo>();
		list.add(new FilterItemInfo(FilterItemInfo.FILTER_TYPE_USER, status));
		final ParcelableUserMention[] mentions = status.mentions;
		if (mentions != null) {
			for (final ParcelableUserMention mention : mentions) {
				if (mention.id != status.user_id) {
					list.add(new FilterItemInfo(FilterItemInfo.FILTER_TYPE_USER, mention));
				}
			}
		}
		final Set<String> hashtags = mExtractor.extractHashtags(status.text_plain);
		if (hashtags != null) {
			for (final String hashtag : hashtags) {
				list.add(new FilterItemInfo(FilterItemInfo.FILTER_TYPE_KEYWORD, hashtag));
			}
		}
		final String source = HtmlEscapeHelper.toPlainText(status.source);
		list.add(new FilterItemInfo(FilterItemInfo.FILTER_TYPE_SOURCE, source));
		return list.toArray(new FilterItemInfo[list.size()]);
	}

	private String getName(final Object value) {
		if (value instanceof ParcelableUserMention) {
			final ParcelableUserMention mention = (ParcelableUserMention) value;
			return getDisplayName(getActivity(), mention.id, mention.name, mention.screen_name);
		} else if (value instanceof ParcelableStatus) {
			final ParcelableStatus status = (ParcelableStatus) value;
			return getDisplayName(getActivity(), status.user_id, status.user_name, status.user_screen_name);
		} else
			return ParseUtils.parseString(value);
	}

	public static AddStatusFilterDialogFragment show(final FragmentManager fm, final ParcelableStatus status) {
		final Bundle args = new Bundle();
		args.putParcelable(EXTRA_STATUS, status);
		final AddStatusFilterDialogFragment f = new AddStatusFilterDialogFragment();
		f.setArguments(args);
		f.show(fm, FRAGMENT_TAG);
		return f;
	}

	private static class FilterItemInfo {

		static final int FILTER_TYPE_USER = 1;
		static final int FILTER_TYPE_KEYWORD = 2;
		static final int FILTER_TYPE_SOURCE = 3;

		final int type;
		final Object value;

		FilterItemInfo(final int type, final Object value) {
			this.type = type;
			this.value = value;
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj) return true;
			if (obj == null) return false;
			if (!(obj instanceof FilterItemInfo)) return false;
			final FilterItemInfo other = (FilterItemInfo) obj;
			if (type != other.type) return false;
			if (value == null) {
				if (other.value != null) return false;
			} else if (!value.equals(other.value)) return false;
			return true;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + type;
			result = prime * result + (value == null ? 0 : value.hashCode());
			return result;
		}

		@Override
		public String toString() {
			return "FilterItemInfo{type=" + type + ", value=" + value + "}";
		}

	}

}

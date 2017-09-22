package com.mediatek.dialer.plugin.calllog;

import android.app.AlertDialog;
import android.app.ListFragment;
import android.os.Bundle;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.CallLog.Calls;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import com.android.dialer.calllog.ContactInfo;
import com.mediatek.dialer.ext.DefaultCallLogExtension;
import com.mediatek.dialer.plugin.OP09DialerPluginUtil;
import com.android.internal.telephony.CallerInfo;
import com.mediatek.calloption.plugin.OP09CallOptionUtils;
import com.mediatek.op09.plugin.R;
import com.mediatek.phone.SIMInfoWrapper;
import com.mediatek.telephony.SimInfoManager;


public class CallLogOP09Extension extends DefaultCallLogExtension
                                   implements CallLogQueryHandler.Listener {

    private static final String TAG = "CallListOP09Extension";
    private static final String EXTRA_CALL_LOG_IDS = "EXTRA_CALL_LOG_IDS";

    private Context mPluginContext;
    private OP09DialerPluginUtil mOP09DialerPlugin;
    private ListFragment mFragment;
    private CallLogQueryHandler mCallLogQueryHandler;

    // sim id of sip call in the call log database
    public static final int CALL_TYPE_SIP = -2;

    public void onCreateForCallLogFragment(Context context, ListFragment fragment) {
        mCallLogQueryHandler = new CallLogQueryHandler(fragment.getActivity().getContentResolver(), this);
        mOP09DialerPlugin = new OP09DialerPluginUtil(context);
        mPluginContext = mOP09DialerPlugin.getPluginContext();
        mFragment = fragment;
    }

    public void onViewCreatedForCallLogFragment(View view, Bundle savedInstanceState) {
        final ListView listView = mFragment.getListView();
        if (null != listView) {
            mFragment.registerForContextMenu(listView);
        }
    }

    public void onDestroyForCallLogFragment() {
        mFragment = null;
    }

    public boolean onListItemClickForCallLogFragment(ListView l, View v, int position, long id) {
        log("onListItemClick(), view = " + v);
        CallLogInfo callLogInfo = (CallLogInfo) v.getTag();
        if (callLogInfo != null) {
            mFragment.getActivity().startActivity(callLogInfo.mCallDetailIntent);
        }
        return true;
    }

    public boolean onCreateContextMenuForCallLogFragment(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
        log("onCreateContextMenu()");
        CallLogInfo callLogInfo =
                (CallLogInfo) ((AdapterView.AdapterContextMenuInfo) menuInfo).targetView.getTag();
        if (null == callLogInfo || null == callLogInfo.mContactInfo) {
            log("onCreateContextMenu(), callLogInfo or callLogInfo.mContactInfo is null, just return");
            return false;
        }

        View titleView = LayoutInflater.from(mPluginContext).inflate(
                                             R.layout.call_log_list_context_menu_title, null);
        TextView mainTitle = (TextView) titleView.findViewById(R.id.main_title);
        TextView subTitle = (TextView) titleView.findViewById(R.id.sub_title);
        if (TextUtils.isEmpty(callLogInfo.mContactInfo.name)) {
            mainTitle.setText(getDisplayNumber(callLogInfo.mContactInfo.number,
                                               callLogInfo.mContactInfo.formattedNumber));
            subTitle.setVisibility(View.GONE);
        } else {
            mainTitle.setText(callLogInfo.mContactInfo.name);
            subTitle.setText(getDisplayNumber(callLogInfo.mContactInfo.number,
                                              callLogInfo.mContactInfo.formattedNumber));
        }
        menu.setHeaderView(titleView);

        boolean bSpecialNumber = (callLogInfo.mContactInfo.number.equals(CallerInfo.UNKNOWN_NUMBER) ||
                                  callLogInfo.mContactInfo.number.equals(CallerInfo.PRIVATE_NUMBER) ||
                                  callLogInfo.mContactInfo.number.equals(CallerInfo.PAYPHONE_NUMBER));

        menu.add(ContextMenu.NONE, R.id.menu_view_call_log_detail, ContextMenu.NONE,
                 mPluginContext.getString(R.string.view_call_log_detail))
            .setIntent(callLogInfo.mCallDetailIntent);

        if (!bSpecialNumber) {
            menu.add(ContextMenu.NONE, R.id.menu_dial_number, ContextMenu.NONE,
                     mPluginContext.getString(R.string.dial_number))
                .setIntent(OP09CallOptionUtils.getCallIntent(callLogInfo.mContactInfo.number));

            menu.add(ContextMenu.NONE, R.id.menu_send_message, ContextMenu.NONE,
                     mPluginContext.getString(R.string.send_message))
                .setIntent(OP09CallOptionUtils.getSMSIntent(callLogInfo.mContactInfo.number));
        }

        menu.add(ContextMenu.NONE, R.id.menu_delete_from_call_log_list, ContextMenu.NONE,
                 mPluginContext.getString(R.string.delete_from_call_log_list));

        if (!bSpecialNumber) {
            if (TextUtils.isEmpty(callLogInfo.mContactInfo.name)) {
                menu.add(ContextMenu.NONE, R.id.menu_save_number_to_contact, ContextMenu.NONE,
                         mPluginContext.getString(R.string.save_number_to_contact))
                    .setIntent(OP09CallOptionUtils.getAddToContactIntent(callLogInfo.mContactInfo.number));
                menu.add(ContextMenu.NONE, R.id.menu_delete_all_calls_with_number, ContextMenu.NONE,
                        mPluginContext.getString(R.string.delete_all_calls_with_number));
            } else {
                menu.add(ContextMenu.NONE, R.id.menu_delete_all_calls_with_contact, ContextMenu.NONE,
                         mPluginContext.getString(R.string.delete_all_calls_with_contact));
            }

            if (0 < SIMInfoWrapper.getDefault().getInsertedSimCount()) {
                menu.add(ContextMenu.NONE, R.id.menu_ip_dial_number, ContextMenu.NONE,
                         mPluginContext.getString(R.string.ip_dial_number))
                    .setIntent(OP09CallOptionUtils.getIPCallIntent(callLogInfo.mContactInfo.number));
            }

            if (!TextUtils.isEmpty(callLogInfo.mContactInfo.name)) {
                menu.add(ContextMenu.NONE, R.id.menu_view_contact_detail, ContextMenu.NONE,
                        mPluginContext.getString(R.string.view_contact_detail))
                    .setIntent(new Intent(Intent.ACTION_VIEW, callLogInfo.mContactInfo.lookupUri));
            }

            menu.add(ContextMenu.NONE, R.id.menu_copy_number, ContextMenu.NONE,
                     mPluginContext.getString(R.string.copy_number));

            menu.add(ContextMenu.NONE, R.id.menu_copy_number_to_dialer, ContextMenu.NONE,
                     mPluginContext.getString(R.string.copy_number_to_dialer))
                .setIntent(OP09CallOptionUtils.getCopyToDialerIntent(callLogInfo.mContactInfo.number));
        }

        return true;
    }

    public boolean onContextItemSelectedForCallLogFragment(MenuItem item) {

        AdapterView.AdapterContextMenuInfo menuInfo
                = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        if (null == menuInfo) {
            return false;
        }
        CallLogInfo callLogInfo = (CallLogInfo) menuInfo.targetView.getTag();
        if (null == callLogInfo) {
            return false;
        }

        switch (item.getItemId()) {

            case R.id.menu_view_call_log_detail:
            case R.id.menu_dial_number:
            case R.id.menu_send_message:
            case R.id.menu_ip_dial_number:
            case R.id.menu_view_contact_detail:
            case R.id.menu_copy_number_to_dialer:
            case R.id.menu_save_number_to_contact:
                mFragment.getActivity().startActivity(item.getIntent());
                return true;

            case R.id.menu_copy_number:
                ClipboardManager clipboardManager =
                         (ClipboardManager) mFragment.getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                // !!!! need to confirm label again
                String label = "label";
                ClipData clipData = ClipData.newPlainText(label, callLogInfo.mContactInfo.number);
                clipboardManager.setPrimaryClip(clipData);
                return true;

            case R.id.menu_delete_from_call_log_list:
                mCallLogQueryHandler.deleteSpecifiedCalls(
                        getDeleteFilterFromCallDetailIntent(callLogInfo.mCallDetailIntent));
                return true;

            case R.id.menu_delete_all_calls_with_number:
                showDeleteAllCallsWithNumberDialog(callLogInfo);
                return true;

            case R.id.menu_delete_all_calls_with_contact:
                showDeleteAllCallsWithContactDialog(callLogInfo);
                return true;

            default:
                break;
        }
        return false;
    }

    public String getDeleteFilterFromCallDetailIntent(Intent callDetailIntent) {
        log("getDeleteFilterFromCallDetailIntent(), callDetailIntent = " + callDetailIntent);
        long[] ids = getCallLogIds(callDetailIntent);
        if (null == ids) {
            log("ids got from intent is null, just return");
            return null;
        }
        StringBuilder where = new StringBuilder("_id in ");
        where.append("(");
        where.append("\'");
        where.append(Long.toString(ids[0]));
        where.append("\'");
        for (int i = 0; i < ids.length; ++i) {
            where.append(",");
            where.append("\'");
            where.append(Long.toString(ids[i]));
            where.append("\'");
        }
        where.append(")");

        log("getDeleteFilter() where ==  " + where.toString());
        return where.toString();
    }

    private long[] getCallLogIds(Intent callDetailIntent) {
        log("getCallLogIds()");
        Uri uri = callDetailIntent.getData();
        if (uri != null) {
            return new long[]{ ContentUris.parseId(uri) };
        }
        return callDetailIntent.getLongArrayExtra(EXTRA_CALL_LOG_IDS);
    }

    public void onCallsDeleted() {
        // !!!! need to check whether need refresh
    }

    private void showDeleteAllCallsWithNumberDialog(final CallLogInfo callLogInfo) {
        AlertDialog dialog;
        AlertDialog.Builder builder = new AlertDialog.Builder(mFragment.getActivity())
                   .setTitle(mPluginContext.getString(R.string.delete_all_calls_with_number_dialog_title))
                   .setIconAttribute(android.R.attr.alertDialogIcon)
                   .setMessage(mPluginContext.getString(R.string.delete_all_calls_with_number_dialog_message))
                   .setNegativeButton(android.R.string.cancel, null)
                   .setPositiveButton(android.R.string.ok,
                       new DialogInterface.OnClickListener() {
                           public void onClick(DialogInterface dialog, int which) {
                               mCallLogQueryHandler.deleteSpecifiedCalls(
                                       Calls.NUMBER + " = '" + callLogInfo.mContactInfo.number + "'");
                           }
                       });
        dialog = builder.create();
        dialog.show();
    }

    private void showDeleteAllCallsWithContactDialog(final CallLogInfo callLogInfo) {
        AlertDialog dialog;
        AlertDialog.Builder builder = new AlertDialog.Builder(mFragment.getActivity())
                   .setTitle(mPluginContext.getString(R.string.delete_all_calls_with_contact_dialog_title))
                   .setIconAttribute(android.R.attr.alertDialogIcon)
                   .setMessage(mPluginContext.getString(R.string.delete_all_calls_with_contact_dialog_message))
                   .setNegativeButton(android.R.string.cancel, null)
                   .setPositiveButton(android.R.string.ok,
                       new DialogInterface.OnClickListener() {
                           public void onClick(DialogInterface dialog, int which) {
                               mCallLogQueryHandler.deleteSpecifiedCalls("calls." + Calls.RAW_CONTACT_ID + " = "
                                                       + Integer.toString(callLogInfo.mContactInfo.rawContactId));
                           }
                       });
        dialog = builder.create();
        dialog.show();
    }

    private CharSequence getDisplayNumber(CharSequence number, CharSequence formattedNumber) {
        if (TextUtils.isEmpty(number)) {
            return "";
        }
        Resources resource = mFragment.getActivity().getResources();
        String packageName = mFragment.getActivity().getPackageName();
        if (number.equals(CallerInfo.UNKNOWN_NUMBER)) {
            return resource.getString(resource.getIdentifier("unknown", "string", packageName));
        }
        if (number.equals(CallerInfo.PRIVATE_NUMBER)) {
            return resource.getString(resource.getIdentifier("private_num", "string", packageName));
        }
        if (number.equals(CallerInfo.PAYPHONE_NUMBER)) {
            return resource.getString(resource.getIdentifier("payphone", "string", packageName));
        }
        if (TextUtils.isEmpty(formattedNumber)) {
            return number;
        } else {
            return formattedNumber;
        }
    }

    /**
     * get sim name by sim id
     *
     * @param simId from datebase
     * @return string sim name
     */
    public void updateSimDisplayNameById(int simId, StringBuffer callDisplayName) {
        callDisplayName.setLength(0);
        callDisplayName.append(" ");
    }

    /**
     * get sim color drawable by sim id
     *
     * @param simId form datebases
     * @return Drawable sim color
     */
    public void updateSimColorDrawable(int simId, Drawable[] drawableSimColor) {
        Drawable dw = null;
        if (CALL_TYPE_SIP == simId) {
            // The request is sip color
            if (null == drawableSimColor[0]) {
                dw = (Drawable) mPluginContext.getResources().getDrawable(R.drawable.dark_small_internet_call);
            }
        } else {
            int colorId = SIMInfoWrapper.getDefault().getInsertedSimColorById(simId);
            if (colorId < 0 || colorId > 3) {
                colorId = 0;
            }
            int simColorResId = SimInfoManager.SimBackgroundDarkSmallRes[colorId];
            dw = (Drawable) mPluginContext.getResources().getDrawable(simColorResId);
        }
        if (null != dw) {
            drawableSimColor[0] = dw;
        } else {
            drawableSimColor[0] = null;
        }
    }

    public boolean setListItemViewTagForCallLogAdapter(View itemView, ContactInfo contactInfo,
                                      Cursor c, Intent callDetailIntent) {
        itemView.setTag(new CallLogInfo(contactInfo, callDetailIntent));
        return true;
    }

    public void bindViewPreForCallLogAdapter(ContactInfo contactInfo) {
        if (0 == mOP09DialerPlugin.getTimezoneRawOffset()) {
            return;
        }
        contactInfo.date += mOP09DialerPlugin.getTimezoneOffset(contactInfo.date);
    }

    private static void log(String msg) {
        Log.d(TAG, msg);
    }
}
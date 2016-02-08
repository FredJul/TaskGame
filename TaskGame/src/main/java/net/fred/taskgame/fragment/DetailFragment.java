/*
 * Copyright (C) 2015 Federico Iosue (federico.iosue@gmail.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.fred.taskgame.fragment;

import android.Manifest;
import android.app.Activity;
import android.app.DatePickerDialog.OnDateSetListener;
import android.app.TimePickerDialog.OnTimeSetListener;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.design.widget.Snackbar;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.DrawerLayout;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.DragEvent;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnDragListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.PopupWindow.OnDismissListener;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.bumptech.glide.load.resource.bitmap.GlideBitmapDrawable;

import net.fred.taskgame.MainApplication;
import net.fred.taskgame.R;
import net.fred.taskgame.activity.CategoryActivity;
import net.fred.taskgame.activity.MainActivity;
import net.fred.taskgame.async.AttachmentTask;
import net.fred.taskgame.model.Attachment;
import net.fred.taskgame.model.Category;
import net.fred.taskgame.model.IdBasedModel;
import net.fred.taskgame.model.Task;
import net.fred.taskgame.model.adapters.AttachmentAdapter;
import net.fred.taskgame.model.adapters.NavDrawerCategoryAdapter;
import net.fred.taskgame.model.listeners.OnAttachingFileListener;
import net.fred.taskgame.model.listeners.OnPermissionRequestedListener;
import net.fred.taskgame.model.listeners.OnReminderPickedListener;
import net.fred.taskgame.utils.Constants;
import net.fred.taskgame.utils.DbHelper;
import net.fred.taskgame.utils.Dog;
import net.fred.taskgame.utils.IntentChecker;
import net.fred.taskgame.utils.KeyboardUtils;
import net.fred.taskgame.utils.LoaderUtils;
import net.fred.taskgame.utils.PermissionsHelper;
import net.fred.taskgame.utils.PrefUtils;
import net.fred.taskgame.utils.ReminderHelper;
import net.fred.taskgame.utils.StorageHelper;
import net.fred.taskgame.utils.UiUtils;
import net.fred.taskgame.utils.date.DateHelper;
import net.fred.taskgame.utils.date.ReminderPickers;
import net.fred.taskgame.view.ExpandableHeightGridView;

import org.parceler.Parcels;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import it.feio.android.checklistview.ChecklistManager;
import it.feio.android.checklistview.interfaces.CheckListChangedListener;
import me.tatarka.rxloader.RxLoaderObserver;
import rx.Observable;
import rx.Subscriber;

import static com.nineoldandroids.view.ViewPropertyAnimator.animate;


public class DetailFragment extends Fragment implements OnReminderPickedListener, OnAttachingFileListener, TextWatcher, CheckListChangedListener {

    private static final int TAKE_PHOTO = 1;
    private static final int TAKE_VIDEO = 2;
    private static final int CATEGORY_CHANGE = 3;
    private static final int FILES = 4;

    public OnDateSetListener onDateSetListener;
    public OnTimeSetListener onTimeSetListener;
    private MediaRecorder mRecorder = null;
    // Toggle isChecklist view
    private View toggleChecklistView;
    private TextView datetime;
    private Uri attachmentUri;
    private AttachmentAdapter mAttachmentAdapter;
    private ExpandableHeightGridView mGridView;
    private PopupWindow attachmentDialog;
    private EditText title, content;
    private Task mTask;
    private Task mOriginalTask;
    // Reminder
    private String reminderDate = "", reminderTime = "";
    private String dateTimeText = "";
    // Audio recording
    private String recordName;
    private MediaPlayer mPlayer = null;
    private boolean isRecording = false;
    private View isPlayingView = null;
    private Bitmap recordingBitmap;
    private ChecklistManager mChecklistManager;
    // Values to print result
    private String exitMessage;
    private UiUtils.MessageType exitMessageStyle = UiUtils.MessageType.TYPE_INFO;
    // Flag to check if after editing it will return to ListActivity or not
    // and in the last case a Toast will be shown instead than Crouton
    private boolean afterSavedReturnsToList = true;
    private boolean orientationChanged;
    private long audioRecordingTimeStart;
    private long audioRecordingTime;
    private Attachment sketchEdited;
    private ScrollView scrollView;
    private Spinner mRewardSpinner;
    private int contentLineCounter = 1;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_detail, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        getMainActivity().getSupportActionBar().setDisplayShowTitleEnabled(false);

        // Force the navigation drawer to stay closed
        getMainActivity().getDrawerLayout().setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        // Restored temp note after orientation change
        if (savedInstanceState != null) {
            mTask = Parcels.unwrap(savedInstanceState.getParcelable("note"));
            mOriginalTask = Parcels.unwrap(savedInstanceState.getParcelable("noteOriginal"));
            attachmentUri = savedInstanceState.getParcelable("attachmentUri");
            orientationChanged = savedInstanceState.getBoolean("orientationChanged");
        }

        // Added the sketched image if present returning from SketchFragment
        if (getMainActivity().sketchUri != null) {
            Attachment attachment = new Attachment();
            attachment.uri = getMainActivity().sketchUri;
            attachment.mimeType = Constants.MIME_TYPE_SKETCH;
            mTask.getAttachmentsList().add(attachment);
            getMainActivity().sketchUri = null;
            // Removes previous version of edited image
            if (sketchEdited != null) {
                mTask.getAttachmentsList().remove(sketchEdited);
                sketchEdited = null;
            }
        }

        init();

        setHasOptionsMenu(true);
        setRetainInstance(false);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        mTask.title = getTaskTitle();
        mTask.content = getTaskContent();
        outState.putParcelable("note", Parcels.wrap(mTask));
        outState.putParcelable("noteOriginal", Parcels.wrap(mOriginalTask));
        outState.putParcelable("attachmentUri", attachmentUri);
        outState.putBoolean("orientationChanged", orientationChanged);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mRecorder != null) {
            mRecorder.release();
            mRecorder = null;
        }

        // Closes keyboard on exit
        if (toggleChecklistView != null) {
            KeyboardUtils.hideKeyboard(toggleChecklistView);
            content.clearFocus();
        }
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (getResources().getConfiguration().orientation != newConfig.orientation) {
            orientationChanged = true;
        }
    }


    private void init() {

        // Handling of Intent actions
        handleIntents();

        if (mOriginalTask == null) {
            mOriginalTask = Parcels.unwrap(getArguments().getParcelable(Constants.INTENT_TASK));
        }

        if (mTask == null) {
            mTask = new Task(mOriginalTask);
        }

        if (mTask.alarmDate != 0) {
            dateTimeText = initReminder(mTask.alarmDate);
        }

        initViews();
    }

    private void handleIntents() {
        Intent i = getActivity().getIntent();

        // Action called from home shortcut
        if (Constants.ACTION_SHORTCUT.equals(i.getAction())
                || Constants.ACTION_NOTIFICATION_CLICK.equals(i.getAction())) {
            afterSavedReturnsToList = false;
            mOriginalTask = DbHelper.getTask(i.getLongExtra(Constants.INTENT_KEY, 0));
            // Checks if the note pointed from the shortcut has been deleted
            if (mOriginalTask == null) {
                getMainActivity().showToast(getText(R.string.shortcut_task_deleted), Toast.LENGTH_LONG);
                getActivity().finish();
            } else {
                mTask = new Task(mOriginalTask);
            }
            i.setAction(null);
        }

        // Check if is launched from a widget
        if (Constants.ACTION_WIDGET.equals(i.getAction())
                || Constants.ACTION_TAKE_PHOTO.equals(i.getAction())) {

            afterSavedReturnsToList = false;

            //  with tags to set tag
            if (i.hasExtra(Constants.INTENT_WIDGET)) {
                String widgetId = i.getExtras().get(Constants.INTENT_WIDGET).toString();
                if (widgetId != null) {
                    long categoryId = PrefUtils.getLong(PrefUtils.PREF_WIDGET_PREFIX + widgetId, -1);
                    if (categoryId != -1) {
                        try {
                            Category cat = DbHelper.getCategory(categoryId);
                            mTask = new Task();
                            mTask.setCategory(cat);
                        } catch (NumberFormatException e) {
                        }
                    }
                }
            }

            // Sub-action is to take a photo
            if (Constants.ACTION_TAKE_PHOTO.equals(i.getAction())) {
                takePhoto();
            }

            i.setAction(null);
        }


        /**
         * Handles third party apps requests of sharing
         */
        if ((Intent.ACTION_SEND.equals(i.getAction())
                || Intent.ACTION_SEND_MULTIPLE.equals(i.getAction())
                || Constants.INTENT_GOOGLE_NOW.equals(i.getAction()))
                && i.getType() != null) {

            afterSavedReturnsToList = false;

            if (mTask == null) {
                mTask = new Task();
            }

            // Text title
            String title = i.getStringExtra(Intent.EXTRA_SUBJECT);
            if (title != null) {
                mTask.title = title;
            }

            // Text content
            String content = i.getStringExtra(Intent.EXTRA_TEXT);
            if (content != null) {
                mTask.content = content;
            }

            // Single attachment data
            Uri uri = i.getParcelableExtra(Intent.EXTRA_STREAM);
            // Due to the fact that Google Now passes intent as text but with
            // audio recording attached the case must be handled in specific way
            if (uri != null && !Constants.INTENT_GOOGLE_NOW.equals(i.getAction())) {
//		    	String mimeType = StorageHelper.getMimeTypeInternal(((MainActivity)getActivity()), i.getType());
//		    	Attachment mAttachment = new Attachment(uri, mimeType);
//		    	if (Constants.MIME_TYPE_FILES.equals(mimeType)) {
//			    	mAttachment.setName(uri.getLastPathSegment());
//		    	}
//		    	noteTmp.addAttachment(mAttachment);
                AttachmentTask task = new AttachmentTask(this, uri, this);
                task.execute();
            }

            // Multiple attachment data
            ArrayList<Uri> uris = i.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (uris != null) {
                for (Uri uriSingle : uris) {
                    AttachmentTask task = new AttachmentTask(this, uriSingle, this);
                    task.execute();
                }
            }

            i.setAction(null);
        }

    }

    private void initViews() {

        // ScrollView container
        scrollView = (ScrollView) getView().findViewById(R.id.content_wrapper);

        // Color of tag marker if note is tagged a function is active in preferences
        setCategoryMarkerColor(mTask.getCategory());

        // Sets links clickable in title and content Views
        title = initTitle();
        requestFocus(title);

        content = initContent();

        // Some fields can be filled by third party application and are always shown
        mGridView = (ExpandableHeightGridView) getView().findViewById(R.id.gridview);
        mAttachmentAdapter = new AttachmentAdapter(getActivity(), mTask.getAttachmentsList());
        mAttachmentAdapter.setOnErrorListener(this);

        // Initialization of gridview for images
        mGridView.setAdapter(mAttachmentAdapter);
        mGridView.autoresize();

        // Click events for images in gridview (zooms image)
        mGridView.setOnItemClickListener(new OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                Attachment attachment = (Attachment) parent.getAdapter().getItem(position);
                if (Constants.MIME_TYPE_AUDIO.equals(attachment.mimeType)) {
                    playback(v, attachment.uri);
                } else {
                    Intent attachmentIntent = new Intent(Intent.ACTION_VIEW);
                    attachmentIntent.setDataAndType(attachment.uri, StorageHelper.getMimeType(getActivity(), attachment.uri));
                    attachmentIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    if (IntentChecker.isAvailable(getActivity().getApplicationContext(), attachmentIntent, null)) {
                        try {
                            startActivity(attachmentIntent);
                        } catch (Exception ignored) {
                        }
                    } else {
                        UiUtils.showWarningMessage(getActivity(), R.string.feature_not_available_on_this_device);
                    }
                }

            }
        });

        // Long click events for images in gridview (removes image)
        mGridView.setOnItemLongClickListener(new OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View v, final int position, long id) {

                // To avoid deleting audio attachment during playback
                if (mPlayer != null) return false;

                MaterialDialog.Builder dialogBuilder = new MaterialDialog.Builder(getActivity())
                        .positiveText(R.string.delete);

                // If is an image user could want to sketch it!
                if (Constants.MIME_TYPE_SKETCH.equals(mAttachmentAdapter.getItem(position).mimeType)) {
                    dialogBuilder
                            .content(R.string.delete_selected_image)
                            .negativeText(R.string.edit)
                            .callback(new MaterialDialog.ButtonCallback() {
                                @Override
                                public void onPositive(MaterialDialog materialDialog) {
                                    mTask.getAttachmentsList().remove(position);
                                    mAttachmentAdapter.notifyDataSetChanged();
                                    mGridView.autoresize();
                                }

                                @Override
                                public void onNegative(MaterialDialog materialDialog) {
                                    sketchEdited = mAttachmentAdapter.getItem(position);
                                    takeSketch(sketchEdited);
                                }
                            });
                } else {
                    dialogBuilder
                            .content(R.string.delete_selected_image)
                            .callback(new MaterialDialog.ButtonCallback() {
                                @Override
                                public void onPositive(MaterialDialog materialDialog) {
                                    mTask.getAttachmentsList().remove(position);
                                    mAttachmentAdapter.notifyDataSetChanged();
                                    mGridView.autoresize();
                                }
                            });
                }

                dialogBuilder.build().show();
                return true;
            }
        });


        // Preparation for reminder icon
        LinearLayout reminder_layout = (LinearLayout) getView().findViewById(R.id.reminder_layout);
        reminder_layout.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                int pickerType = PrefUtils.getBoolean("settings_simple_calendar", false) ? ReminderPickers.TYPE_AOSP : ReminderPickers.TYPE_GOOGLE;
                ReminderPickers reminderPicker = new ReminderPickers(getActivity(), DetailFragment.this, pickerType);
                reminderPicker.pick(mTask.alarmDate);
                onDateSetListener = reminderPicker;
                onTimeSetListener = reminderPicker;
            }
        });
        reminder_layout.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                MaterialDialog dialog = new MaterialDialog.Builder(getActivity())
                        .content(R.string.remove_reminder)
                        .positiveText(R.string.ok)
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog materialDialog) {
                                reminderDate = "";
                                reminderTime = "";
                                mTask.alarmDate = 0;
                                datetime.setText("");
                            }
                        }).build();
                dialog.show();
                return true;
            }
        });


        // Reminder
        datetime = (TextView) getView().findViewById(R.id.datetime);
        datetime.setText(dateTimeText);

        // Footer dates of creation...
        TextView creationTextView = (TextView) getView().findViewById(R.id.creation);
        String creation = mTask.getCreationShort(getActivity());
        creationTextView.append(creation.length() > 0 ? getString(R.string.creation, creation) : "");
        if (creationTextView.getText().length() == 0)
            creationTextView.setVisibility(View.GONE);

        // ... and last modification
        TextView lastModificationTextView = (TextView) getView().findViewById(R.id.last_modification);
        String lastModification = mTask.getLastModificationShort(getActivity());
        lastModificationTextView.append(lastModification.length() > 0 ? getString(R.string.last_update, lastModification) : "");
        if (lastModificationTextView.getText().length() == 0)
            lastModificationTextView.setVisibility(View.GONE);

        mRewardSpinner = (Spinner) getView().findViewById(R.id.rewardSpinner);
        if (mTask.pointReward == Task.LOW_POINT_REWARD) {
            mRewardSpinner.setSelection(0);
        } else if (mTask.pointReward == Task.NORMAL_POINT_REWARD) {
            mRewardSpinner.setSelection(1);
        } else if (mTask.pointReward == Task.HIGH_POINT_REWARD) {
            mRewardSpinner.setSelection(2);
        } else if (mTask.pointReward == Task.VERY_HIGH_POINT_REWARD) {
            mRewardSpinner.setSelection(3);
        }

        mRewardSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0:
                        mTask.pointReward = Task.LOW_POINT_REWARD;
                        break;
                    case 1:
                        mTask.pointReward = Task.NORMAL_POINT_REWARD;
                        break;
                    case 2:
                        mTask.pointReward = Task.HIGH_POINT_REWARD;
                        break;
                    case 3:
                        mTask.pointReward = Task.VERY_HIGH_POINT_REWARD;
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
    }

    private EditText initTitle() {
        EditText title = (EditText) getView().findViewById(R.id.detail_title);
        title.setText(mTask.title);
        // To avoid dropping here the dragged isChecklist items
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            title.setOnDragListener(new OnDragListener() {
                @Override
                public boolean onDrag(View v, DragEvent event) {
//					((View)event.getLocalState()).setVisibility(View.VISIBLE);
                    return true;
                }
            });
        }
        //When editor action is pressed focus is moved to last character in content field
        title.setOnEditorActionListener(new android.widget.TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(android.widget.TextView v, int actionId, KeyEvent event) {
                content.requestFocus();
                content.setSelection(content.getText().length());
                return false;
            }
        });
        return title;
    }

    private EditText initContent() {
        EditText content = (EditText) getView().findViewById(R.id.detail_content);
        content.setText(mTask.content);
        // Avoid focused line goes under the keyboard
        content.addTextChangedListener(this);

        // Restore isChecklist
        toggleChecklistView = content;
        if (mTask.isChecklist) {
            mTask.isChecklist = false;
            toggleChecklistView.setAlpha(0);
            toggleChecklist2();
        }

        return content;
    }


    /**
     * Force focus and shows soft keyboard
     */
    private void requestFocus(final EditText view) {
        if (mTask.equals(new Task())) { // if the current task is totally empty
            KeyboardUtils.showKeyboard(view);
        }
    }


    /**
     * Colors tag marker in note's title and content elements
     */
    private void setCategoryMarkerColor(Category tag) {
        View marker = getView().findViewById(R.id.tag_marker);
        // Coloring the target
        if (tag != null && tag.color != null) {
            marker.setBackgroundColor(Integer.parseInt(tag.color));
        } else {
            marker.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    private MainActivity getMainActivity() {
        return (MainActivity) getActivity();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        if (TextUtils.isEmpty(mTask.questId)) { // no menu for quests
            inflater.inflate(R.menu.menu_detail, menu);
        }

        super.onCreateOptionsMenu(menu, inflater);
    }


    @Override
    public void onPrepareOptionsMenu(Menu menu) {

        if (!TextUtils.isEmpty(mTask.questId)) { // no menu for quests
            return;
        }

        // Closes search view if left open in List fragment
        MenuItem searchMenuItem = menu.findItem(R.id.menu_search);
        if (searchMenuItem != null) {
            MenuItemCompat.collapseActionView(searchMenuItem);
        }

        boolean newNote = mTask.id == IdBasedModel.INVALID_ID;

        menu.findItem(R.id.menu_checklist_on).setVisible(!mTask.isChecklist);
        menu.findItem(R.id.menu_checklist_off).setVisible(mTask.isChecklist);
        // If note is isTrashed only this options will be available from menu
        if (mTask.isTrashed) {
            menu.findItem(R.id.menu_untrash).setVisible(true);
            menu.findItem(R.id.menu_delete).setVisible(true);
            // Otherwise all other actions will be available
        } else {
            menu.findItem(R.id.menu_trash).setVisible(!newNote);
        }
    }

    public boolean goHome() {
        stopPlaying();

        // The activity has managed a shared intent from third party app and
        // performs a normal onBackPressed instead of returning back to ListActivity
        if (!afterSavedReturnsToList) {
            if (!TextUtils.isEmpty(exitMessage)) {
                getMainActivity().showToast(exitMessage, Toast.LENGTH_SHORT);
            }
            getActivity().finish();
            return true;
        } else {
            if (!TextUtils.isEmpty(exitMessage) && exitMessageStyle != null) {
                UiUtils.showMessage(getActivity(), exitMessage, exitMessageStyle);
            }
        }

        // Otherwise the result is passed to ListActivity
        if (getActivity() != null && getActivity().getSupportFragmentManager() != null) {
            getActivity().getSupportFragmentManager().popBackStack();
            if (getActivity().getSupportFragmentManager().getBackStackEntryCount() == 1) {
                getMainActivity().getSupportActionBar().setDisplayShowTitleEnabled(true);
            }
            if (getMainActivity().getDrawerToggle() != null) {
                getMainActivity().getDrawerToggle().setDrawerIndicatorEnabled(true);
            }
        }

        if (getActivity().getSupportFragmentManager().getBackStackEntryCount() == 1) {
            getMainActivity().animateBurger(MainActivity.BURGER);
        }

        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                navigateUp();
                break;
            case R.id.menu_attachment:
                showPopup(getActivity().findViewById(R.id.menu_attachment));
                break;
            case R.id.menu_category:
                categorizeNote();
                break;
            case R.id.menu_share:
                shareTask();
                break;
            case R.id.menu_checklist_on:
                toggleChecklist();
                break;
            case R.id.menu_checklist_off:
                toggleChecklist();
                break;
            case R.id.menu_trash:
                trashTask(true);
                break;
            case R.id.menu_untrash:
                trashTask(false);
                break;
            case R.id.menu_discard_changes:
                discard();
                break;
            case R.id.menu_delete:
                deleteTask();
                break;
        }
        return super.onOptionsItemSelected(item);
    }


    private void navigateUp() {
        afterSavedReturnsToList = true;
        saveAndExit();
    }

    private void toggleChecklist() {

        // In case isChecklist is active a prompt will ask about many options
        // to decide hot to convert back to simple text
        if (!mTask.isChecklist) {
            toggleChecklist2();
            return;
        }

        // If isChecklist is active but no items are checked the conversion in done automatically
        // without prompting user
        if (mChecklistManager.getCheckedCount() == 0) {
            toggleChecklist2(true, false);
            return;
        }

        // Inflate the popup_layout.xml
        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
        final View layout = inflater.inflate(R.layout.dialog_remove_checklist_layout, (ViewGroup) getView().findViewById(R.id.layout_root));

        // Retrieves options checkboxes and initialize their values
        final CheckBox keepChecked = (CheckBox) layout.findViewById(R.id.checklist_keep_checked);
        final CheckBox keepCheckmarks = (CheckBox) layout.findViewById(R.id.checklist_keep_checkmarks);
        keepChecked.setChecked(PrefUtils.getBoolean(PrefUtils.PREF_KEEP_CHECKED, true));
        keepCheckmarks.setChecked(PrefUtils.getBoolean(PrefUtils.PREF_KEEP_CHECKMARKS, true));

        new MaterialDialog.Builder(getActivity())
                .customView(layout, false)
                .positiveText(R.string.ok)
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog materialDialog) {
                        PrefUtils.putBoolean(PrefUtils.PREF_KEEP_CHECKED, keepChecked.isChecked());
                        PrefUtils.putBoolean(PrefUtils.PREF_KEEP_CHECKMARKS, keepCheckmarks.isChecked());

                        toggleChecklist2();
                    }
                }).build().show();
    }


    /**
     * Toggles isChecklist view
     */
    private void toggleChecklist2() {
        boolean keepChecked = PrefUtils.getBoolean(PrefUtils.PREF_KEEP_CHECKED, true);
        boolean showChecks = PrefUtils.getBoolean(PrefUtils.PREF_KEEP_CHECKMARKS, true);
        toggleChecklist2(keepChecked, showChecks);
    }

    private void toggleChecklist2(final boolean keepChecked, final boolean showChecks) {
        // Get instance and set options to convert EditText to CheckListView
        mChecklistManager = ChecklistManager.getInstance(getActivity());
        mChecklistManager.setMoveCheckedOnBottom(Integer.valueOf(PrefUtils.getString("settings_checked_items_behavior",
                String.valueOf(it.feio.android.checklistview.Settings.CHECKED_HOLD))));
        mChecklistManager.setShowChecks(true);
        mChecklistManager.setNewEntryHint(getString(R.string.checklist_item_hint));

        // Links parsing options
        mChecklistManager.addTextChangedListener(this);
        mChecklistManager.setCheckListChangedListener(this);

        // Options for converting back to simple text
        mChecklistManager.setKeepChecked(keepChecked);
        mChecklistManager.setShowChecks(showChecks);

        // Vibration
        mChecklistManager.setDragVibrationEnabled(true);

        // Switches the views
        View newView = mChecklistManager.convert(toggleChecklistView);

        // Switches the views
        if (newView != null) {
            mChecklistManager.replaceViews(toggleChecklistView, newView);
            toggleChecklistView = newView;
//			fade(toggleChecklistView, true);
            animate(toggleChecklistView).alpha(1).scaleXBy(0).scaleX(1).scaleYBy(0).scaleY(1);
            mTask.isChecklist = !mTask.isChecklist;
        }
    }


    /**
     * Categorize note choosing from a list of previously created categories
     */
    private void categorizeNote() {
        // Retrieves all available categories
        final List<Category> categories = DbHelper.getCategories();

        final MaterialDialog dialog = new MaterialDialog.Builder(getActivity())
                .title(R.string.categorize_as)
                .adapter(new NavDrawerCategoryAdapter(getActivity(), categories), null)
                .positiveText(R.string.add_category)
                .negativeText(R.string.remove_category)
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(MaterialDialog dialog, DialogAction which) {
                        Intent intent = new Intent(getActivity(), CategoryActivity.class);
                        intent.putExtra("noHome", true);
                        startActivityForResult(intent, CATEGORY_CHANGE);
                    }
                })
                .onNegative(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(MaterialDialog dialog, DialogAction which) {
                        mTask.setCategory(null);
                        setCategoryMarkerColor(null);
                    }
                })
                .build();

        dialog.getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                mTask.setCategory(categories.get(position));
                setCategoryMarkerColor(categories.get(position));
                dialog.dismiss();
            }
        });

        dialog.show();
    }


    // The method that displays the popup.
    private void showPopup(View anchor) {
        DisplayMetrics metrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);

        // Inflate the popup_layout.xml
        LayoutInflater inflater = (LayoutInflater) getActivity().getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
        View layout = inflater.inflate(R.layout.attachment_dialog, null);

        // Creating the PopupWindow
        attachmentDialog = new PopupWindow(getActivity());
        attachmentDialog.setContentView(layout);
        attachmentDialog.setWidth(WindowManager.LayoutParams.WRAP_CONTENT);
        attachmentDialog.setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
        attachmentDialog.setFocusable(true);
        attachmentDialog.setOnDismissListener(new OnDismissListener() {
            @Override
            public void onDismiss() {
                if (isRecording) {
                    isRecording = false;
                    stopRecording();
                }
            }
        });

        // Clear the default translucent background
        attachmentDialog.setBackgroundDrawable(new BitmapDrawable());

        // Camera
        android.widget.TextView cameraSelection = (android.widget.TextView) layout.findViewById(R.id.camera);
        cameraSelection.setOnClickListener(new AttachmentOnClickListener());
        // Audio recording
        android.widget.TextView recordingSelection = (android.widget.TextView) layout.findViewById(R.id.recording);
        recordingSelection.setOnClickListener(new AttachmentOnClickListener());
        // Video recording
        android.widget.TextView videoSelection = (android.widget.TextView) layout.findViewById(R.id.video);
        videoSelection.setOnClickListener(new AttachmentOnClickListener());
        // Files
        android.widget.TextView filesSelection = (android.widget.TextView) layout.findViewById(R.id.files);
        filesSelection.setOnClickListener(new AttachmentOnClickListener());
        // Sketch
        android.widget.TextView sketchSelection = (android.widget.TextView) layout.findViewById(R.id.sketch);
        sketchSelection.setOnClickListener(new AttachmentOnClickListener());

        try {
            attachmentDialog.showAsDropDown(anchor);
        } catch (Exception e) {
            Snackbar.make(getActivity().findViewById(R.id.content), R.string.error, Snackbar.LENGTH_SHORT).show();
        }
    }

    private void takePhoto() {
        // Checks for camera app available
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (!IntentChecker.isAvailable(getActivity(), intent, new String[]{PackageManager.FEATURE_CAMERA})) {
            UiUtils.showWarningMessage(getActivity(), R.string.feature_not_available_on_this_device);
            return;
        }
        // Checks for created file validity
        File f = StorageHelper.createNewAttachmentFile(getActivity(), Constants.MIME_TYPE_IMAGE_EXT);
        if (f == null) {
            UiUtils.showErrorMessage(getActivity(), R.string.error);
            return;
        }
        // Launches intent
        attachmentUri = Uri.fromFile(f);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, attachmentUri);
        startActivityForResult(intent, TAKE_PHOTO);
    }

    private void takeVideo() {
        Intent takeVideoIntent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        if (!IntentChecker.isAvailable(getActivity(), takeVideoIntent, new String[]{PackageManager.FEATURE_CAMERA})) {
            UiUtils.showWarningMessage(getActivity(), R.string.feature_not_available_on_this_device);
            return;
        }
        // File is stored in custom ON folder to speedup the attachment
        File f = StorageHelper.createNewAttachmentFile(getActivity(), Constants.MIME_TYPE_VIDEO_EXT);
        if (f == null) {
            UiUtils.showErrorMessage(getActivity(), R.string.error);
            return;
        }
        attachmentUri = Uri.fromFile(f);
        takeVideoIntent.putExtra(MediaStore.EXTRA_OUTPUT, attachmentUri);

        String maxVideoSizeStr = "".equals(PrefUtils.getString("settings_max_video_size", "")) ? "0" : PrefUtils.getString("settings_max_video_size", "");
        int maxVideoSize = Integer.parseInt(maxVideoSizeStr);
        takeVideoIntent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, Long.valueOf(maxVideoSize * 1024 * 1024));
        startActivityForResult(takeVideoIntent, TAKE_VIDEO);
    }

    private void takeSketch(Attachment attachment) {
        File f = StorageHelper.createNewAttachmentFile(getActivity(), Constants.MIME_TYPE_SKETCH_EXT);
        if (f == null) {
            UiUtils.showErrorMessage(getActivity(), R.string.error);
            return;
        }
        attachmentUri = Uri.fromFile(f);

        // Forces portrait orientation to this fragment only
        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Fragments replacing
        FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
        UiUtils.animateTransition(transaction, UiUtils.TRANSITION_HORIZONTAL);
        SketchFragment sketchFragment = new SketchFragment();
        Bundle b = new Bundle();
        b.putParcelable(MediaStore.EXTRA_OUTPUT, attachmentUri);
        if (attachment != null) {
            b.putParcelable("base", attachment.uri);
        }
        sketchFragment.setArguments(b);
        transaction.replace(R.id.fragment_container, sketchFragment, getMainActivity().FRAGMENT_SKETCH_TAG).addToBackStack(getMainActivity().FRAGMENT_DETAIL_TAG).commit();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        // Fetch uri from activities, store into adapter and refresh adapter
        Attachment attachment = new Attachment();
        attachment.uri = attachmentUri;

        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case TAKE_PHOTO:
                    attachment.mimeType = Constants.MIME_TYPE_IMAGE;
                    mTask.getAttachmentsList().add(attachment);
                    mAttachmentAdapter.notifyDataSetChanged();
                    mGridView.autoresize();
                    break;
                case TAKE_VIDEO:
                    attachment.mimeType = Constants.MIME_TYPE_VIDEO;
                    mTask.getAttachmentsList().add(attachment);
                    mAttachmentAdapter.notifyDataSetChanged();
                    mGridView.autoresize();
                    break;
                case FILES:
                    onActivityResultManageReceivedFiles(intent);
                    break;
                case CATEGORY_CHANGE:
                    UiUtils.showMessage(getActivity(), R.string.category_saved);
                    Category category = Parcels.unwrap(intent.getParcelableExtra(Constants.INTENT_CATEGORY));
                    mTask.setCategory(category);
                    setCategoryMarkerColor(category);
                    break;
            }
        }
    }

    private void onActivityResultManageReceivedFiles(Intent intent) {
        List<Uri> uris = new ArrayList<>();
        if (intent.getClipData() != null) {
            for (int i = 0; i < intent.getClipData().getItemCount(); i++) {
                uris.add(intent.getClipData().getItemAt(i).getUri());
            }
        } else {
            uris.add(intent.getData());
        }
        for (Uri uri : uris) {
            new AttachmentTask(this, uri, this).execute();
        }
    }


    /**
     * Discards changes done to the note and eventually delete new attachments
     */
    private void discard() {
        // Checks if some new files have been attached and must be removed
        if (!mTask.getAttachmentsList().equals(mOriginalTask.getAttachmentsList())) {
            for (Attachment newAttachment : mTask.getAttachmentsList()) {
                if (!mOriginalTask.getAttachmentsList().contains(newAttachment)) {
                    StorageHelper.delete(getActivity(), newAttachment.uri.getPath());
                }
            }
        }

        goHome();
    }

    private void trashTask(boolean trash) {
        // Simply go back if is a new note
        if (mTask.id == IdBasedModel.INVALID_ID) {
            goHome();
            return;
        }

        mTask.isTrashed = trash;
        exitMessage = trash ? getString(R.string.task_trashed) : getString(R.string.task_untrashed);
        exitMessageStyle = trash ? UiUtils.MessageType.TYPE_WARN : UiUtils.MessageType.TYPE_INFO;
        if (trash) {
            ReminderHelper.removeReminder(MainApplication.getContext(), mTask);
        } else {
            ReminderHelper.addReminder(MainApplication.getContext(), mTask);
        }
        saveTask();
    }

    private void deleteTask() {
        // Confirm dialog creation
        new MaterialDialog.Builder(getActivity())
                .content(R.string.delete_task_confirmation)
                .positiveText(R.string.ok)
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(MaterialDialog dialog, DialogAction which) {
                        DbHelper.deleteTask(mTask);
                        UiUtils.showMessage(getActivity(), R.string.task_deleted);
                        goHome();
                    }
                }).build().show();
    }

    public void saveAndExit() {
        if (TextUtils.isEmpty(mTask.questId)) { // do not modify any quests
            exitMessage = getString(R.string.task_updated);
            exitMessageStyle = UiUtils.MessageType.TYPE_INFO;
            saveTask();
        } else {
            goHome();
        }
    }


    /**
     * Save new tasks, modify them or archive
     */
    void saveTask() {
        // Changed fields
        mTask.title = getTaskTitle();
        mTask.content = getTaskContent();

        // Check if some text or attachments of any type have been inserted or
        // is an empty note
        if (TextUtils.isEmpty(mTask.title) && TextUtils.isEmpty(mTask.content)
                && mTask.getAttachmentsList().size() == 0) {

            exitMessage = getString(R.string.empty_task_not_saved);
            exitMessageStyle = UiUtils.MessageType.TYPE_INFO;
            goHome();
            return;
        }

        if (saveNotNeeded()) {
            return;
        }

        LoaderUtils.startAsync(this, new Observable.OnSubscribe<Void>() {
            @Override
            public void call(Subscriber<? super Void> subscriber) {
                // purge attachments
                for (Attachment oldAttachment : mOriginalTask.getAttachmentsList()) {
                    boolean stillHere = false;
                    for (Attachment currentAttachment : mTask.getAttachmentsList()) {
                        if (currentAttachment.id != IdBasedModel.INVALID_ID && currentAttachment.id == oldAttachment.id) {
                            stillHere = true;
                            break;
                        }
                    }

                    if (!stillHere) {
                        DbHelper.deleteAttachment(oldAttachment);
                    }
                }

                // Note updating on database
                DbHelper.updateTask(mTask, lastModificationUpdatedNeeded());

                // Set reminder if is not passed yet
                long now = Calendar.getInstance().getTimeInMillis();
                if (mTask.alarmDate >= now) {
                    ReminderHelper.addReminder(MainApplication.getContext(), mTask);
                }

                subscriber.onNext(null);
            }
        }, new RxLoaderObserver<Void>() {

            @Override
            public void onNext(Void result) {
                goHome();
            }
        });
    }


    /**
     * Checks if nothing is changed to avoid committing if possible (check)
     */
    private boolean saveNotNeeded() {
        if (mTask.equals(mOriginalTask)) {
            exitMessage = "";
            goHome();
            return true;
        }
        return false;
    }


    /**
     * Checks if only tag, archive or trash status have been changed
     * and then force to not update last modification date*
     */
    private boolean lastModificationUpdatedNeeded() {
        Task tmpTask = new Task(mTask);
        tmpTask.setCategory(mTask.getCategory());
        tmpTask.isTrashed = mTask.isTrashed;
        return !tmpTask.equals(mOriginalTask);
    }

    private String getTaskTitle() {
        String res;
        if (getActivity() != null && getActivity().findViewById(R.id.detail_title) != null) {
            Editable editableTitle = ((EditText) getActivity().findViewById(R.id.detail_title)).getText();
            res = TextUtils.isEmpty(editableTitle) ? "" : editableTitle.toString();
        } else {
            res = title.getText() != null ? title.getText().toString() : "";
        }
        return res;
    }

    private String getTaskContent() {
        String content = "";
        if (!mTask.isChecklist) {
            try {
                try {
                    content = ((EditText) getActivity().findViewById(R.id.detail_content)).getText().toString();
                } catch (ClassCastException e) {
                    content = ((android.widget.EditText) getActivity().findViewById(R.id.detail_content)).getText().toString();
                }
            } catch (NullPointerException e) {
            }
        } else {
            if (mChecklistManager != null) {
                mChecklistManager.setKeepChecked(true);
                mChecklistManager.setShowChecks(true);
                content = mChecklistManager.getText();
            }
        }
        return content;
    }

    /**
     * Updates share intent
     */
    private void shareTask() {
        mTask.title = getTaskTitle();
        mTask.content = getTaskContent();
        mTask.share(getActivity());
    }

    /**
     * Used to set actual reminder state when initializing a note to be edited
     */
    private String initReminder(long reminderDateTime) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(reminderDateTime);
        reminderDate = DateHelper.onDateSet(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH), Constants.DATE_FORMAT_SHORT_DATE);
        reminderTime = DateHelper.getTimeShort(getActivity(), cal.getTimeInMillis());
        return getString(R.string.alarm_set_on) + " " + reminderDate + " " + getString(R.string.at_time)
                + " " + reminderTime;
    }

    /**
     * Audio recordings playback
     */
    private void playback(View v, Uri uri) {
        // Some recording is playing right now
        if (mPlayer != null && mPlayer.isPlaying()) {
            // If the audio actually played is NOT the one from the click view the last one is played
            if (isPlayingView != v) {
                stopPlaying();
                isPlayingView = v;
                startPlaying(uri);
                recordingBitmap = ((BitmapDrawable) ((ImageView) v.findViewById(R.id.gridview_item_picture)).getDrawable()).getBitmap();
                ((ImageView) v.findViewById(R.id.gridview_item_picture)).setImageResource(R.drawable.stop);
                // Otherwise just stops playing
            } else {
                stopPlaying();
            }
            // If nothing is playing audio just plays
        } else {
            isPlayingView = v;
            startPlaying(uri);
            Drawable d = ((ImageView) v.findViewById(R.id.gridview_item_picture)).getDrawable();
            if (BitmapDrawable.class.isAssignableFrom(d.getClass())) {
                recordingBitmap = ((BitmapDrawable) d).getBitmap();
            } else {
                recordingBitmap = ((GlideBitmapDrawable) d.getCurrent()).getBitmap();
            }
            ((ImageView) v.findViewById(R.id.gridview_item_picture)).setImageResource(R.drawable.stop);
        }
    }

    private void startPlaying(Uri uri) {
        if (mPlayer == null) {
            mPlayer = new MediaPlayer();
        }
        try {
            mPlayer.setDataSource(getActivity(), uri);
            mPlayer.prepare();
            mPlayer.start();
            mPlayer.setOnCompletionListener(new OnCompletionListener() {

                @Override
                public void onCompletion(MediaPlayer mp) {
                    mPlayer = null;
                    ((ImageView) isPlayingView.findViewById(R.id.gridview_item_picture)).setImageBitmap(recordingBitmap);
                    recordingBitmap = null;
                    isPlayingView = null;
                }
            });
        } catch (IOException e) {
            Dog.e("prepare() failed", e);
            UiUtils.showErrorMessage(getActivity(), R.string.error);
        }
    }

    private void stopPlaying() {
        if (mPlayer != null) {
            ((ImageView) isPlayingView.findViewById(R.id.gridview_item_picture)).setImageBitmap(recordingBitmap);
            isPlayingView = null;
            recordingBitmap = null;
            mPlayer.release();
            mPlayer = null;
        }
    }

    private void startRecording(final View v) {
        PermissionsHelper.requestPermission(getActivity(), Manifest.permission.RECORD_AUDIO,
                R.string.permission_audio_recording, new OnPermissionRequestedListener() {
                    @Override
                    public void onPermissionGranted() {
                        isRecording = true;
                        android.widget.TextView mTextView = (android.widget.TextView) v;
                        mTextView.setText(getString(R.string.stop));
                        mTextView.setTextColor(Color.parseColor("#ff0000"));

                        File f = StorageHelper.createNewAttachmentFile(getActivity(), Constants.MIME_TYPE_AUDIO_EXT);
                        if (f == null) {
                            UiUtils.showErrorMessage(getActivity(), R.string.error);
                            return;
                        }

                        if (mRecorder == null) {
                            mRecorder = new MediaRecorder();
                            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_WB);
                            mRecorder.setAudioEncodingBitRate(16);
                            mRecorder.setAudioSamplingRate(44100);
                        }
                        recordName = f.getAbsolutePath();
                        mRecorder.setOutputFile(recordName);

                        try {
                            audioRecordingTimeStart = Calendar.getInstance().getTimeInMillis();
                            mRecorder.prepare();
                            mRecorder.start();
                        } catch (IOException | IllegalStateException e) {
                            Dog.e("prepare() failed", e);
                            UiUtils.showErrorMessage(getActivity(), R.string.error);
                        }
                    }
                });
    }

    private void stopRecording() {
        if (mRecorder != null) {
            mRecorder.stop();
            audioRecordingTime = Calendar.getInstance().getTimeInMillis() - audioRecordingTimeStart;
            mRecorder.release();
            mRecorder = null;
        }
    }

    @Override
    public void onAttachingFileErrorOccurred(Attachment mAttachment) {
        UiUtils.showErrorMessage(getActivity(), R.string.error_saving_attachments);
        if (mTask.getAttachmentsList().contains(mAttachment)) {
            mTask.getAttachmentsList().remove(mAttachment);
            mAttachmentAdapter.notifyDataSetChanged();
            mGridView.autoresize();
        }
    }

    @Override
    public void onAttachingFileFinished(Attachment mAttachment) {
        mTask.getAttachmentsList().add(mAttachment);
        mAttachmentAdapter.notifyDataSetChanged();
        mGridView.autoresize();
    }

    @Override
    public void onReminderPicked(long reminder) {
        mTask.alarmDate = reminder;
        if (isAdded()) {
            datetime.setText(getString(R.string.alarm_set_on) + " " + DateHelper.getDateTimeShort(getActivity(), reminder));
        }
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        scrollContent();
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count,
                                  int after) {
    }

    @Override
    public void afterTextChanged(Editable s) {
    }

    @Override
    public void onCheckListChanged() {
        scrollContent();
    }

    private void scrollContent() {
        if (mTask.isChecklist) {
            if (mChecklistManager.getCount() > contentLineCounter) {
                scrollView.scrollBy(0, 60);
            }
            contentLineCounter = mChecklistManager.getCount();
        } else {
            if (content.getLineCount() > contentLineCounter) {
                scrollView.scrollBy(0, 60);
            }
            contentLineCounter = content.getLineCount();
        }
    }

    /**
     * Used to check currently opened note from activity to avoid openind multiple times the same one
     */
    public Task getCurrentTask() {
        return mTask;
    }

    /**
     * Manages clicks on attachment dialog
     */
    private class AttachmentOnClickListener implements OnClickListener {

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                // Photo from camera
                case R.id.camera:
                    takePhoto();
                    attachmentDialog.dismiss();
                    break;
                case R.id.recording:
                    if (!isRecording) {
                        isRecording = true;
                        android.widget.TextView mTextView = (android.widget.TextView) v;
                        mTextView.setText(getString(R.string.stop));
                        mTextView.setTextColor(Color.parseColor("#ff0000"));
                        startRecording(v);
                    } else {
                        isRecording = false;
                        stopRecording();
                        Attachment attachment = new Attachment();
                        attachment.uri = Uri.parse(recordName);
                        attachment.mimeType = Constants.MIME_TYPE_AUDIO;
                        attachment.length = audioRecordingTime;
                        mTask.getAttachmentsList().add(attachment);
                        mAttachmentAdapter.notifyDataSetChanged();
                        mGridView.autoresize();
                        attachmentDialog.dismiss();
                    }
                    break;
                case R.id.video:
                    takeVideo();
                    attachmentDialog.dismiss();
                    break;
                case R.id.files:
                    Intent filesIntent;
                    filesIntent = new Intent(Intent.ACTION_GET_CONTENT);
                    filesIntent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                    filesIntent.addCategory(Intent.CATEGORY_OPENABLE);
                    filesIntent.setType("*/*");
                    startActivityForResult(filesIntent, FILES);
                    attachmentDialog.dismiss();
                    break;
                case R.id.sketch:
                    takeSketch(null);
                    attachmentDialog.dismiss();
                    break;
            }
        }
    }
}




/*
 * Copyright 2011 Azwan Adli Abdullah
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gh4a.activities;

import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TextView;

import com.gh4a.BaseActivity;
import com.gh4a.Constants;
import com.gh4a.Gh4Application;
import com.gh4a.ProgressDialogTask;
import com.gh4a.R;
import com.gh4a.utils.IntentUtils;
import com.gh4a.utils.UiUtils;

import org.eclipse.egit.github.core.Milestone;
import org.eclipse.egit.github.core.RepositoryId;
import org.eclipse.egit.github.core.service.MilestoneService;

import java.io.IOException;
import java.util.Calendar;
import java.util.Date;

public class IssueMilestoneEditActivity extends BaseActivity implements View.OnClickListener {
    public static final String EXTRA_MILESTONE = "milestone";

    private String mRepoOwner;
    private String mRepoName;

    private Milestone mMilestone;

    private TextInputLayout mTitleWrapper;
    private EditText mTitleView;
    private EditText mDescriptionView;
    private TextView mDueView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Gh4Application.get().isAuthorized()) {
            Intent intent = new Intent(this, Github4AndroidActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.issue_create_milestone);

        LayoutInflater headerInflater = LayoutInflater.from(UiUtils.makeHeaderThemedContext(this));
        View header = headerInflater.inflate(R.layout.issue_create_header, null);
        addHeaderView(header, false);

        mTitleWrapper = (TextInputLayout) header.findViewById(R.id.title_wrapper);
        mTitleView = (EditText) header.findViewById(R.id.et_title);
        mDescriptionView = (EditText) header.findViewById(R.id.et_desc);
        mDueView = (TextView) findViewById(R.id.tv_due);

        CoordinatorLayout rootLayout = getRootLayout();
        FloatingActionButton fab = (FloatingActionButton)
                getLayoutInflater().inflate(R.layout.accept_fab, rootLayout, false);
        fab.setOnClickListener(this);
        rootLayout.addView(fab);

        findViewById(R.id.due_container).setOnClickListener(this);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle(isInEditMode()
                ? R.string.issue_milestone_edit : R.string.issue_milestone_new);
        actionBar.setSubtitle(mRepoOwner + "/" + mRepoName);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);

        if (mMilestone == null) {
            mMilestone = new Milestone();
            mMilestone.setState("open");
        }

        mTitleView.addTextChangedListener(new UiUtils.ButtonEnableTextWatcher(mTitleView, fab));
        mTitleView.addTextChangedListener(new UiUtils.EmptinessWatchingTextWatcher(mTitleView) {
            @Override
            public void onIsEmpty(boolean isEmpty) {
                if (isEmpty) {
                    mTitleWrapper.setError(getString(R.string.issue_error_milestone_title));
                } else {
                    mTitleWrapper.setErrorEnabled(false);
                }
            }
        });

        mTitleView.setText(mMilestone.getTitle());
        mDescriptionView.setText(mMilestone.getDescription());
        updateLabels();
        setToolbarScrollable(false);
    }

    @Override
    protected void onInitExtras(Bundle extras) {
        super.onInitExtras(extras);
        mRepoOwner = extras.getString(Constants.Repository.OWNER);
        mRepoName = extras.getString(Constants.Repository.NAME);
        mMilestone = (Milestone) extras.getSerializable(EXTRA_MILESTONE);
    }

    private boolean isInEditMode() {
        return getIntent().hasExtra(EXTRA_MILESTONE);
    }

    private void openIssueMilestones() {
        Intent intent = new Intent(this, IssueMilestoneListActivity.class);
        intent.putExtra(Constants.Repository.OWNER, mRepoOwner);
        intent.putExtra(Constants.Repository.NAME, mRepoName);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    @Override
    protected boolean canSwipeToRefresh() {
        // swipe-to-refresh doesn't make much sense in the
        // interaction model of this activity
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (isInEditMode()) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.accept_delete, menu);
            menu.removeItem(R.id.accept);
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    protected Intent navigateUp() {
        return IntentUtils.getIssueListActivityIntent(this, mRepoOwner, mRepoName,
                Constants.Issue.STATE_OPEN);
    }

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.due_container) {
            DialogFragment newFragment = new DatePickerFragment();
            newFragment.show(getSupportFragmentManager(), "datePicker");
        } else if (view instanceof FloatingActionButton) {
            String title = mTitleView.getText().toString();
            String desc = mDescriptionView.getText() != null ?
                    mDescriptionView.getText().toString() : null;

            mMilestone.setTitle(title);
            mMilestone.setDescription(desc);
            new SaveIssueMilestoneTask(mMilestone).schedule();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.delete:
                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.issue_dialog_delete_title,
                                mMilestone.getTitle()))
                        .setMessage(R.string.issue_dialog_delete_message)
                        .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int whichButton) {
                                new DeleteIssueMilestoneTask(mMilestone.getNumber()).schedule();
                            }
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setDueOn(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, day);
        cal.set(Calendar.MONTH, month);
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);

        mMilestone.setDueOn(cal.getTime());
        updateLabels();
    }

    private void resetDueOn() {
        mMilestone.setDueOn(null);
        updateLabels();
    }

    private void updateLabels() {
        Date dueOn = mMilestone.getDueOn();

        if (dueOn != null) {
            mDueView.setText(DateFormat.getMediumDateFormat(this).format(dueOn));
        } else {
            mDueView.setText(R.string.issue_milestone_due_unset);
        }
    }

    private class SaveIssueMilestoneTask extends ProgressDialogTask<Void> {
        private Milestone mMilestone;

        public SaveIssueMilestoneTask(Milestone milestone) {
            super(IssueMilestoneEditActivity.this, 0, R.string.saving_msg);
            mMilestone = milestone;
        }

        @Override
        protected ProgressDialogTask<Void> clone() {
            return new SaveIssueMilestoneTask(mMilestone);
        }

        @Override
        protected Void run() throws IOException {
            MilestoneService milestoneService = (MilestoneService)
                    Gh4Application.get().getService(Gh4Application.MILESTONE_SERVICE);

            RepositoryId repoId = new RepositoryId(mRepoOwner, mRepoName);
            if (isInEditMode()) {
                milestoneService.editMilestone(repoId, mMilestone);
            } else {
                milestoneService.createMilestone(repoId, mMilestone);
            }

            return null;
        }

        @Override
        protected void onSuccess(Void result) {
            openIssueMilestones();
        }

        @Override
        protected String getErrorMessage() {
            return getContext().getString(R.string.issue_error_create_milestone,
                    mMilestone.getTitle());
        }
    }

    private class DeleteIssueMilestoneTask extends ProgressDialogTask<Void> {
        private int mNumber;

        public DeleteIssueMilestoneTask(int number) {
            super(IssueMilestoneEditActivity.this, 0, R.string.deleting_msg);
            mNumber = number;
        }

        @Override
        protected ProgressDialogTask<Void> clone() {
            return new DeleteIssueMilestoneTask(mNumber);
        }

        @Override
        protected Void run() throws IOException {
            MilestoneService milestoneService = (MilestoneService)
                    Gh4Application.get().getService(Gh4Application.MILESTONE_SERVICE);
            milestoneService.deleteMilestone(mRepoOwner, mRepoName, mNumber);
            return null;
        }

        @Override
        protected void onSuccess(Void result) {
            openIssueMilestones();
        }

        @Override
        protected String getErrorMessage() {
            return getContext().getString(R.string.issue_error_delete_milestone);
        }
    }

    public static class DatePickerFragment extends DialogFragment
            implements DatePickerDialog.OnDateSetListener, DialogInterface.OnClickListener {
        private boolean mStopping;

        @Override
        public @NonNull Dialog onCreateDialog(@NonNull Bundle savedInstanceState) {
            final IssueMilestoneEditActivity activity = (IssueMilestoneEditActivity) getActivity();
            final Calendar c = Calendar.getInstance();

            int year = c.get(Calendar.YEAR);
            int month = c.get(Calendar.MONTH);
            int day = c.get(Calendar.DAY_OF_MONTH);

            Date dueOn = activity.mMilestone.getDueOn();
            if (dueOn != null) {
                c.setTime(dueOn);
                year = c.get(Calendar.YEAR);
                month = c.get(Calendar.MONTH);
                day = c.get(Calendar.DAY_OF_MONTH);
            }
            DatePickerDialog dialog = new DatePickerDialog(activity, this, year, month, day) {
                @Override
                protected void onStop() {
                    mStopping = true;
                    super.onStop();
                    mStopping = false;
                }
            };
            dialog.setButton(DialogInterface.BUTTON_NEUTRAL, getString(R.string.unset), this);
            return dialog;
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            if (which == DialogInterface.BUTTON_NEUTRAL) {
                getEditActivity().resetDueOn();
            }
        }

        @Override
        public void onDateSet(DatePicker view, int year, int month, int day) {
            if (!mStopping) {
                getEditActivity().setDueOn(year, month, day);
            }
        }

        private IssueMilestoneEditActivity getEditActivity() {
            return (IssueMilestoneEditActivity) getActivity();
        }
    }
}

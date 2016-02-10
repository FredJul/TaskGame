package net.fred.taskgame.service;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Service;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SyncResult;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;

import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesStatusCodes;
import com.google.android.gms.games.quest.Quest;
import com.google.android.gms.games.quest.QuestBuffer;
import com.google.android.gms.games.quest.Quests;
import com.google.android.gms.games.snapshot.Snapshot;
import com.google.android.gms.games.snapshot.SnapshotMetadataChange;
import com.google.android.gms.games.snapshot.Snapshots;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.raizlabs.android.dbflow.sql.language.Delete;
import com.raizlabs.android.dbflow.sql.language.Select;

import net.fred.taskgame.model.Attachment;
import net.fred.taskgame.model.Attachment_Table;
import net.fred.taskgame.model.Category;
import net.fred.taskgame.model.SyncData;
import net.fred.taskgame.model.Task;
import net.fred.taskgame.model.Task_Table;
import net.fred.taskgame.provider.FakeProvider;
import net.fred.taskgame.utils.Constants;
import net.fred.taskgame.utils.Dog;
import net.fred.taskgame.utils.GameHelper;
import net.fred.taskgame.utils.PrefUtils;

import java.nio.charset.Charset;

/**
 * Service to handle sync requests.
 * <p>
 * <p>This service is invoked in response to Intents with action android.content.SyncAdapter, and
 * returns a Binder connection to SyncAdapter.
 * <p>
 * <p>For performance, only one sync adapter will be initialized within this application's context.
 * <p>
 * <p>Note: The SyncService itself is not notified when a new sync occurs. It's role is to
 * manage the lifecycle of our {@link SyncAdapter} and provide a handle to said SyncAdapter to the
 * OS on request.
 */
public class SyncService extends Service {

    public static final long SYNC_INTERVAL = 24 * 60L * 60L; // Every day

    private static final Object sSyncAdapterLock = new Object();
    private static SyncAdapter sSyncAdapter = null;

    /**
     * Thread-safe constructor, creates static {@link SyncAdapter} instance.
     */
    @Override
    public void onCreate() {
        super.onCreate();
        Dog.i("Service created");
        synchronized (sSyncAdapterLock) {
            if (sSyncAdapter == null) {
                sSyncAdapter = new SyncAdapter(getApplicationContext(), true);
            }
        }
    }

    @Override
    /**
     * Logging-only destructor.
     */
    public void onDestroy() {
        super.onDestroy();
        Dog.i("Service destroyed");
    }

    /**
     * Return Binder handle for IPC communication with {@link SyncAdapter}.
     * <p>
     * <p>New sync requests will be sent directly to the SyncAdapter using this channel.
     *
     * @param intent Calling intent
     * @return Binder handle for {@link SyncAdapter}
     */
    @Override
    public IBinder onBind(Intent intent) {
        return sSyncAdapter.getSyncAdapterBinder();
    }

    public static void triggerSync(Context context, boolean force) {
        AccountManager manager = (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);
        Account[] accounts = manager.getAccountsByType("com.google");

        if (accounts != null && accounts.length > 0) {
            ContentResolver.setIsSyncable(accounts[0], FakeProvider.AUTHORITY, 1);
            ContentResolver.setSyncAutomatically(accounts[0], FakeProvider.AUTHORITY, true);
            ContentResolver.addPeriodicSync(accounts[0], FakeProvider.AUTHORITY, Bundle.EMPTY, SYNC_INTERVAL);

            Dog.i("Google account found");
            if (force) {
                Bundle extras = new Bundle();
                // Disable sync backoff and ignore sync preferences. In other words...perform sync NOW!
                extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
                extras.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
                ContentResolver.requestSync(accounts[0], FakeProvider.AUTHORITY, extras);
            } else {
                ContentResolver.requestSync(accounts[0], FakeProvider.AUTHORITY, Bundle.EMPTY);
            }
        }
    }

    /**
     * Define a sync adapter for the app.
     * <p>
     * <p>This class is instantiated in {@link SyncService}, which also binds SyncAdapter to the system.
     * SyncAdapter should only be initialized in SyncService, never anywhere else.
     * <p>
     * <p>The system calls onPerformSync() via an RPC call through the IBinder object supplied by
     * SyncService.
     */
    public static class SyncAdapter extends AbstractThreadedSyncAdapter {

        /**
         * Constructor. Obtains handle to content resolver for later use.
         */
        public SyncAdapter(Context context, boolean autoInitialize) {
            super(context, autoInitialize);
        }

        /**
         * Constructor. Obtains handle to content resolver for later use.
         */
        public SyncAdapter(Context context, boolean autoInitialize, boolean allowParallelSyncs) {
            super(context, autoInitialize, allowParallelSyncs);
        }

        /**
         * Called by the Android system in response to a request to run the sync adapter. The work
         * required to read data from the network, parse it, and store it in the content provider is
         * done here. Extending AbstractThreadedSyncAdapter ensures that all methods within SyncAdapter
         * run on a background thread. For this reason, blocking I/O and other long-running tasks can be
         * run <em>in situ</em>, and you don't have to set up a separate thread for them.
         * .
         * <p>
         * <p>This is where we actually perform any work required to perform a sync.
         * {@link AbstractThreadedSyncAdapter} guarantees that this will be called on a non-UI thread,
         * so it is safe to perform blocking I/O here.
         * <p>
         * <p>The syncResult argument allows you to pass information back to the method that triggered
         * the sync.
         */
        @Override
        public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
            Dog.i("Beginning network synchronization");

            if (PrefUtils.getBoolean(PrefUtils.PREF_ALREADY_LOGGED_TO_GAMES, false)) {
                try {
                    GameHelper helper = new GameHelper(getContext());
                    helper.setup(null);
                    helper.getApiClient().blockingConnect();

                    // Open the saved game using its name.
                    Snapshots.OpenSnapshotResult result = Games.Snapshots.open(helper.getApiClient(), "save", true).await();

                    Snapshot snapshot = result.getSnapshot();
                    Gson gson = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create();

                    // Check the result of the open operation
                    if (result.getStatus().isSuccess()) {
                        // Read the byte content of the saved game.
                        byte[] savedBytes = snapshot.getSnapshotContents().readFully();

                        String json = new String(savedBytes);
                        Dog.i("get back " + json);
                        SyncData syncedData = gson.fromJson(json, SyncData.class);

                        if (syncedData.lastSyncDate > PrefUtils.getLong(PrefUtils.PREF_LAST_SYNC_DATE, -1)) {
                            PrefUtils.putLong(PrefUtils.PREF_CURRENT_POINTS, syncedData.currentPoints);
                            Delete.tables(Category.class, Task.class);
                            for (Category cat : syncedData.categories) {
                                Dog.i("write cat " + cat);
                                cat.save();
                            }
                            for (Task task : syncedData.tasks) {
                                task.save();
                            }
                        }

                        SyncData syncData = SyncData.getLastData();
                        json = gson.toJson(syncData);
                        Dog.i("write " + json);

                        // Sync leaderboard
                        Games.Leaderboards.submitScore(helper.getApiClient(), Constants.LEADERBOARD_ID, syncData.currentPoints);

                        // Set the data payload for the snapshot
                        snapshot.getSnapshotContents().writeBytes(json.getBytes());

                        // Create the change operation
                        SnapshotMetadataChange metadataChange = new SnapshotMetadataChange.Builder().build();

                        // Commit the operation
                        Games.Snapshots.commitAndClose(helper.getApiClient(), snapshot, metadataChange);
                        PrefUtils.putLong(PrefUtils.PREF_LAST_SYNC_DATE, syncData.lastSyncDate);

                    } else if (result.getStatus().getStatusCode() == GamesStatusCodes.STATUS_SNAPSHOT_CONFLICT) {
                        Dog.i("Save conflict, use last version");

                        SyncData syncData = SyncData.getLastData();
                        String json = gson.toJson(syncData);
                        Dog.i("write " + json);

                        Snapshot conflictSnapshot = result.getConflictingSnapshot();
                        // Resolve between conflicts by selecting the newest of the conflicting snapshots.
                        Snapshot resolvedSnapshot = snapshot;

                        if (snapshot.getMetadata().getLastModifiedTimestamp() <
                                conflictSnapshot.getMetadata().getLastModifiedTimestamp()) {
                            resolvedSnapshot = conflictSnapshot;
                        }
                        // Set the data payload for the snapshot
                        snapshot.getSnapshotContents().writeBytes(json.getBytes());
                        Games.Snapshots.resolveConflict(helper.getApiClient(), result.getConflictId(), resolvedSnapshot);
                        PrefUtils.putLong(PrefUtils.PREF_LAST_SYNC_DATE, syncData.lastSyncDate);
                    } else {
                        Dog.i("error status: " + result.getStatus().getStatusCode());
                        return; // We just got a timeout, it's maybe because we left, it's better to not continue
                    }

                    Dog.i("retrieve quests");
                    Quests.LoadQuestsResult questsResult = Games.Quests.load(helper.getApiClient(), new int[]{Games.Quests.SELECT_ACCEPTED}, Games.Quests.SORT_ORDER_ENDING_SOON_FIRST, false).await();
                    if (questsResult.getStatus().isSuccess()) {
                        Dog.i("retrieve quests success");
                        QuestBuffer quests = questsResult.getQuests();
                        for (Quest quest : quests) {
                            Dog.i("quest: " + quest);
                            String questId = quest.getQuestId();
                            Task task = new Select().from(Task.class).where(Task_Table.questId.eq(questId)).querySingle();
                            if (task == null) {
                                task = new Task();
                            }

                            task.questId = questId;
                            task.title = quest.getName();
                            task.content = quest.getDescription();
                            String reward = new String(quest.getCurrentMilestone().getCompletionRewardData(), Charset.forName("UTF-8"));
                            Dog.i("quest reward: " + reward);
                            task.pointReward = Integer.valueOf(reward);

                            task.save();

                            // Delete task's attachments
                            Delete.table(Attachment.class, Attachment_Table.taskId.eq(task.id));
                            Attachment attachment = new Attachment();
                            attachment.taskId = task.id;
                            attachment.mimeType = Constants.MIME_TYPE_IMAGE;
                            attachment.uri = Uri.parse(quest.getIconImageUrl());
                            attachment.save();
                        }
                    }
                } catch (Throwable t) {
                    Dog.e("ERROR", t);
                    syncResult.databaseError = true;
                }

            } else {
                Dog.i("No yet registered to play games");
                syncResult.databaseError = true;
            }

            Dog.i("Network synchronization complete");
        }
    }
}

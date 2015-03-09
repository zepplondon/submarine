package com.submarine.gameservices;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import com.badlogic.gdx.Gdx;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.GamesStatusCodes;
import com.google.android.gms.games.snapshot.Snapshot;
import com.google.android.gms.games.snapshot.SnapshotMetadataChange;
import com.google.android.gms.games.snapshot.Snapshots;
import com.google.example.games.basegameutils.GameHelper;

public class AndroidGameServices implements GameHelper.GameHelperListener, GameServices {
    // The AppState slot we are editing.  For simplicity this sample only manipulates a single
    // Cloud Save slot and a corresponding Snapshot entry,  This could be changed to any integer
    // 0-3 without changing functionality (Cloud Save has four slots, numbered 0-3).
    private static final String TAG = "com.submarine.gameservices.AndroidGameServices";
    private Activity activity;
    private GameHelper gameHelper;
    private GameServicesListener<Snapshots.OpenSnapshotResult> gameServicesListener;


    public AndroidGameServices(Activity activity) {
        this.activity = activity;
        gameHelper = new GameHelper(this.activity, (GameHelper.CLIENT_GAMES | GameHelper.CLIENT_SNAPSHOT));
        gameHelper.setup(this);
        gameHelper.enableDebugLog(true);
    }

    public GoogleApiClient getApiClient() {
        return gameHelper.getApiClient();
    }

    public void onActivityResult(int request, int response, Intent data) {
        gameHelper.onActivityResult(request, response, data);
    }


    // // ************** GOOGLE PART ***************\\\\\\\

    @Override
    public void onSignInFailed() {
        Gdx.app.log(TAG, "Sing in Fail");
        gameHelper.showFailureDialog();
        if (gameServicesListener != null) {
            gameServicesListener.onSignInFailed();
        }
    }

    @Override
    public void onSignInSucceeded() {
        Gdx.app.log(TAG, "Sign in success");
        if (gameServicesListener != null) {
            gameServicesListener.onSignInSucceeded();
        }
    }

    // // ************** END GOOGLE PART ***************\\\\\\\

    @Override
    public boolean isSignedIn() {
        return gameHelper.isSignedIn();
    }

    @Override
    public void login() {
        if (gameHelper.isConnecting()) {
            return;
        }
        gameHelper.onStart(activity);
    }

    @Override
    public void logout() {
        gameHelper.onStop();
    }

    @Override
    public void submitScore(final String leaderBoardId, final long score) {
        if (isSignedIn()) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Games.Leaderboards.submitScore(gameHelper.getApiClient(), leaderBoardId, score);
                }
            });
        }
    }

    @Override
    public void showLeaderBoard(final String leaderBoardId) {
        Gdx.app.log(TAG, "Show Leaderboard : " + isSignedIn());
        if (isSignedIn()) {
            activity.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    Gdx.app.log(TAG, "Show Leaderboard");
                    activity.startActivityForResult(Games.Leaderboards.getLeaderboardIntent(gameHelper.getApiClient(), leaderBoardId), 2);
                }
            });
        }
    }


    /**
     * Load a Snapshot from the Saved Games service based on its unique name.  After load, the UI
     * will update to display the Snapshot data and SnapshotMetadata.
     *
     * @param snapshotName the unique name of the Snapshot.
     */
    @Override
    public void savedGamesLoad(String snapshotName, boolean createIfMissing) {
        PendingResult<Snapshots.OpenSnapshotResult> pendingResult = Games.Snapshots.open(
                gameHelper.getApiClient(), snapshotName, createIfMissing);

        ResultCallback<Snapshots.OpenSnapshotResult> callback =
                new ResultCallback<Snapshots.OpenSnapshotResult>() {
                    @Override
                    public void onResult(Snapshots.OpenSnapshotResult openSnapshotResult) {
                        int status = openSnapshotResult.getStatus().getStatusCode();
                        switch (status) {
                            case GamesStatusCodes.STATUS_OK:
                            case GamesStatusCodes.STATUS_SNAPSHOT_CONTENTS_UNAVAILABLE:
                                gameServicesListener.savedGamesLoadSucceeded(openSnapshotResult);
                                break;
                            case GamesStatusCodes.STATUS_SNAPSHOT_CONFLICT:
                                gameServicesListener.savedGamesLoadConflicted(openSnapshotResult, 0);
                                break;
                            default:
                                gameServicesListener.savedGamesLoadFailed(openSnapshotResult);
                                break;
                        }
                    }
                };
        pendingResult.setResultCallback(callback);
    }


    /**
     * Update the Snapshot in the Saved Games service with new data.  Metadata is not affected,
     * however for your own application you will likely want to update metadata such as cover image,
     * played time, and description with each Snapshot update.  After update, the UI will
     * be cleared.
     */
    @Override
    public void savedGamesUpdate(final String snapshotName, final byte[] data, final boolean createIfMissing) {
        AsyncTask<Void, Void, Boolean> updateTask = new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                Snapshots.OpenSnapshotResult open = Games.Snapshots.open(
                        gameHelper.getApiClient(), snapshotName, createIfMissing).await();

                if (!open.getStatus().isSuccess()) {
                    Gdx.app.log(TAG, "Could not open Snapshot for update.");
                    gameServicesListener.savedGamesUpdateFailed();
                    return false;
                }

                // Change data but leave existing metadata
                Snapshot snapshot = open.getSnapshot();
                snapshot.getSnapshotContents().writeBytes(data);

                Snapshots.CommitSnapshotResult commit = Games.Snapshots.commitAndClose(
                        gameHelper.getApiClient(), snapshot, SnapshotMetadataChange.EMPTY_CHANGE).await();

                if (!commit.getStatus().isSuccess()) {
                    Gdx.app.log(TAG, "Failed to commit Snapshot.");
                    gameServicesListener.savedGamesUpdateFailed();
                    return false;
                }
                // No failures
                Gdx.app.log(TAG, "No failures for Snapshot update.");
                gameServicesListener.savedGamesUpdateSucceeded();
                return true;
            }
        };
        updateTask.execute();
    }

    public Snapshots.OpenSnapshotResult resolveSavedGamesConflict(String conflictId, Snapshot resolvedSnapshot) {
        return Games.Snapshots.resolveConflict(gameHelper.getApiClient(), conflictId, resolvedSnapshot).await();
    }

    @Override
    public void setListener(GameServicesListener gameServicesListener) {
        this.gameServicesListener = gameServicesListener;
    }

    @Override
    public void unlockAchievement(final String achievementId) {
        if (isSignedIn()) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Games.Achievements.unlock(gameHelper.getApiClient(), achievementId);
                }
            });
        }
    }

    @Override
    public void incrementAchievement(final String achievementId, final int incrementAmount) {
        if (isSignedIn()) {
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Games.Achievements.increment(gameHelper.getApiClient(), achievementId, incrementAmount);
                }
            });
        }
    }

    @Override
    public void showAchievements() {
        Gdx.app.log(TAG, "Show Achievements : " + isSignedIn());
        if (isSignedIn()) {
            activity.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    Gdx.app.log(TAG, "Show Achievements");
                    activity.startActivityForResult(Games.Achievements.getAchievementsIntent(gameHelper.getApiClient()), 1);
                }
            });
        }
    }
}
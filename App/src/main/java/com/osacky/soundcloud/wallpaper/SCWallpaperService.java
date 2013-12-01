package com.osacky.soundcloud.wallpaper;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;

import com.osacky.soundcloud.wallpaper.models.Track;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SCWallpaperService extends WallpaperService {

    @Override
    public Engine onCreateEngine() {
        return new SCWallpaperEngine();
    }

    private class SCWallpaperEngine extends Engine implements SharedPreferences.OnSharedPreferenceChangeListener {
        private static final int WAVE_WIDTH = 1800;
        private static final int WAVE_HEIGHT = 280;
        private static final int SCROLL_THRESHOLD = 40;
        private static final int TOUCH_TIME = 150;
        private static final int ALBUM_ART_SIZE = 300;
        private static final String TOUCH_KEY = "touch";
        private static final String USER_KEY = "user";
        private static final String REFRESH_INTERVAL_KEY = "refresh_interval";
        private static final String TAG = "SCWallpaperEngine";

        private final Handler handler = new Handler();
        private final Runnable drawRunner = new Runnable() {
            @Override
            public void run() {
                if (waveform != null)
                    drawBackground();
            }
        };
        private final Runnable refreshData = new Runnable() {
            @Override
            public void run() {
                new DownloadFavoritesTask().execute();
            }
        };
        private final Rect source = new Rect(0, 0, WAVE_WIDTH, WAVE_HEIGHT);
        private final Rect sourceAlbum = new Rect(0, 0, 100, 100);
        private Rect albumArt;
        private Rect dest;
        private Paint textPaint = new Paint();
        private Paint backgroundPaint = new Paint();

        // preferences
        private boolean touchEnabled;
        private String username;
        private int refresh_interval;

        // current track
        private Bitmap waveform;
        private Bitmap artWork;
        private int index;

        private List<Track> tracks = new ArrayList<Track>();
        private int xPixelOffset = 0;
        private int yPixelOffset = 0;
        private Boolean needsRescheduling = false;

        // for tracking taps
        private int mDownX;
        private int mDownY;
        private boolean isClick = false;


        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(SCWallpaperService.this);
            preferences.registerOnSharedPreferenceChangeListener(this);

            touchEnabled = preferences.getBoolean(TOUCH_KEY, false);
            username = preferences.getString(USER_KEY, "nosacky");
            refresh_interval = Integer.parseInt(preferences.getString(REFRESH_INTERVAL_KEY, "30"));

            // set up objects at initialization (it is expensive to create them during the draw method)
            textPaint.setStyle(Paint.Style.FILL);
            textPaint.setAntiAlias(true);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextSize(50);
            backgroundPaint.setColor(getResources().getColor(R.color.orange));

            int halfHeight = getDesiredMinimumHeight()/2;
            int halfWave = WAVE_HEIGHT/2;
            dest = new Rect(0, halfHeight - halfWave , getDesiredMinimumWidth(), halfHeight + halfWave);
            albumArt = new Rect(0, halfHeight - ALBUM_ART_SIZE - halfWave, ALBUM_ART_SIZE, halfHeight - halfWave);

            // refresh that data!
            handler.post(refreshData);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            if (visible) {
                handler.post(drawRunner);
                if (needsRescheduling) {
                    handler.postDelayed(refreshData, refresh_interval);
                }
            } else {
                handler.removeCallbacks(drawRunner);
            }
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
            handler.removeCallbacks(drawRunner);
            handler.removeCallbacks(refreshData);
        }


        @Override
        public void onTouchEvent(MotionEvent event) {
            if (touchEnabled) {
                // detect user touches
                switch (event.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN:
                        mDownX = (int) event.getX();
                        mDownY = (int) event.getY();
                        isClick = true;
                        break;
                    case MotionEvent.ACTION_CANCEL:
                    case MotionEvent.ACTION_UP:
                        // make sure the user has long pressed so there aren't any accidental touches
                        if (isClick && event.getEventTime() - event.getDownTime() > TOUCH_TIME) {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setData(Uri.parse(tracks.get(index).getUrl()));
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (isClick && (Math.abs(mDownX - event.getX()) > SCROLL_THRESHOLD || Math.abs(mDownY - event.getY()) > SCROLL_THRESHOLD)) {
                            isClick = false;
                        }
                        break;
                    default:
                        break;
                }
            }
        }

        @Override
        public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {
            this.xPixelOffset = xPixelOffset;
            this.yPixelOffset = yPixelOffset;
            if (waveform == null) {
                return;
            }
            // scroll
            handler.post(drawRunner);
        }

        // draw the wallpaper!
        private void drawBackground() {
            Track track = tracks.get(index);
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            try {
                canvas = holder.lockCanvas();
                if (canvas != null) {
                    canvas.drawColor(getResources().getColor(R.color.orange));
                    if (artWork != null) {
                        canvas.drawBitmap(artWork, sourceAlbum, albumArt, null);
                        canvas.drawText(track.getTitle(), ALBUM_ART_SIZE, getDesiredMinimumHeight()/2 - ALBUM_ART_SIZE, textPaint);
                    } else {
                        canvas.drawText(track.getTitle(), 0, getDesiredMinimumHeight()/2 - ALBUM_ART_SIZE, textPaint);
                    }
                    canvas.translate(xPixelOffset, yPixelOffset);
                    canvas.drawBitmap(waveform, source, dest, null);
                }
            } finally {
                if (canvas != null)
                    holder.unlockCanvasAndPost(canvas);
            }
            // remove other callbacks in case draw runner was called many times
            handler.removeCallbacks(drawRunner);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            if (key.equals(TOUCH_KEY)) {
                touchEnabled = sharedPreferences.getBoolean(TOUCH_KEY, false);
            } else if (key.equals(USER_KEY)) {
                username = sharedPreferences.getString(USER_KEY, "nosacky");
            } else if (key.equals(REFRESH_INTERVAL_KEY)) {
                refresh_interval = Integer.parseInt(sharedPreferences.getString(REFRESH_INTERVAL_KEY, "5"));
            }
        }

        private class DownloadFavoritesTask extends AsyncTask<Void, Void, List<Track>> {
            private static final String USERS_URL = "http://api.soundcloud.com/users/";
            private static final String FAVS_END_URL = "/favorites.json?client_id=";

            @Override
            protected List<Track> doInBackground(Void... params) {
                try {
                    String client_id = getResources().getString(R.string.client_id);
                    URL url = new URL(USERS_URL + username + FAVS_END_URL + client_id);
                    InputStream inputStream = url.openStream();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, Charset.forName("UTF-8")));
                    StringBuilder stringBuilder = new StringBuilder();
                    int cp;
                    while ((cp = reader.read()) != -1) {
                        stringBuilder.append((char) cp);
                    }
                    inputStream.close();
                    JSONArray jsonArray = new JSONArray(stringBuilder.toString());
                    List<Track> result = new ArrayList<Track>();
                    for (int i = 0; i < jsonArray.length(); i++) {
                        Track track = new Track(jsonArray.getJSONObject(i));
                        result.add(track);
                    }
                    return result;
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(List<Track> result) {
                if (result != null) {
                    tracks = result;
                    new ImageDownloadTask().execute();
                }
                // refresh again later if screen is currently visible (no point in fetching twice while screen is off)
                if (isVisible()) {
                    long refreshInterval = TimeUnit.SECONDS.toMillis(refresh_interval);
                    // make sure there's only one scheduled
                    handler.removeCallbacks(refreshData);
                    handler.postDelayed(refreshData, refreshInterval);
                } else {
                    needsRescheduling = true;
                }
            }
        }

        private class ImageDownloadTask extends AsyncTask<String, Void, ResultHolder> {

            @Override
            protected ResultHolder doInBackground(String... params) {
                try {
                    ResultHolder resultHolder = new ResultHolder();
                    int randIndex = (int) (tracks.size() * Math.random());
                    resultHolder.index = randIndex;

                    Track track = tracks.get(randIndex);
                    URL url = track.getWaveformURL();

                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.connect();
                    InputStream inputStream = connection.getInputStream();

                    resultHolder.waveform = Utils.invert(BitmapFactory.decodeStream(inputStream));

                    URL url2 = track.getArtworkURL();

                    if (url2 != null) {
                        HttpURLConnection connection2 = (HttpURLConnection) url2.openConnection();
                        connection2.connect();
                        InputStream inputStream2 = connection2.getInputStream();

                        resultHolder.artWork = BitmapFactory.decodeStream(inputStream2);
                    }

                    return resultHolder;
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(ResultHolder success) {
                // don't change anything if this fails
                if (success != null) {
                    if (waveform != null) {
                        waveform.recycle();
                        waveform = null;
                    }
                    if (artWork != null) {
                        artWork.recycle();
                        artWork = null;
                    }
                    index = success.index;
                    waveform = success.waveform;
                    artWork = success.artWork;
                    if (isVisible()) {
                        handler.post(drawRunner);
                    }
                }
            }
        }

        // We must use a result holder so that we can pass back the downloaded images only if we
        // are successful in grabbing them
        private class ResultHolder {
            public Bitmap waveform;
            public Bitmap artWork;
            public int index;
        }
    }
}

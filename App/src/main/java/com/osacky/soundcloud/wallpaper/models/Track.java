package com.osacky.soundcloud.wallpaper.models;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;

public class Track {
    private static final String ID_ID = "id";
    private static final String TITLE_ID = "title";
    private static final String USER_ID = "user";
    private static final String USERNAME_ID = "username";
    private static final String PERMALINK_ID = "permalink_url";
    private static final String ARTWORK_ID = "artwork_url";
    private static final String WAVEFORM_ID = "waveform_url";

    private long id;
    private String title;
    private String username;
    private String url;
    private URL artworkURL;
    private URL waveformURL;

    public Track(JSONObject jsonObject) throws JSONException, MalformedURLException {
        id = Long.parseLong(jsonObject.getString(ID_ID));
        title = jsonObject.getString(TITLE_ID);
        JSONObject user = jsonObject.getJSONObject(USER_ID);
        username = user.getString(USERNAME_ID);

        // some of these are null or "null" so we have to do this
        String permalinkString = jsonObject.getString(PERMALINK_ID);
        if (permalinkString != null && !permalinkString.equals("null")) {
            url = permalinkString;
        }
        String artString = jsonObject.getString(ARTWORK_ID);
        if (artString != null && !artString.equals("null")) {
            artworkURL = new URL(artString);
        }
        String waveString = jsonObject.getString(WAVEFORM_ID);
        if (waveString != null && !waveString.equals("null")) {
            waveformURL = new URL(waveString);
        }
    }

    public Track(long id, String title, String url, String username, URL artworkURL, URL waveformURL) {
        this.id = id;
        this.title = title;
        this.username = username;
        this.url = url;
        this.artworkURL = artworkURL;
        this.waveformURL = waveformURL;
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public URL getArtworkURL() {
        return artworkURL;
    }

    public URL getWaveformURL() {
        return waveformURL;
    }
}

package de.shadow578.yetanothervideoplayer.ui;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import de.shadow578.yetanothervideoplayer.R;
import de.shadow578.yetanothervideoplayer.util.ConfigKeys;
import de.shadow578.yetanothervideoplayer.util.Logging;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

import java.util.Locale;

public class LaunchActivity extends AppCompatActivity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch);
        Logging.logD("Launch Activity onCreate was called.");

        //get splash screen duration
        int splashDuration = getResources().getInteger(R.integer.min_splash_screen_duration);

        //post event to start playback activity delayed
        Handler splashHandler = new Handler();
        splashHandler.postDelayed(new Runnable()
        {
            @Override
            public void run()
            {
                //launch the playback activity
                if (launchPlayback(getIntent()))
                {
                    //launched ok, close this activity as soon as playback activity closes
                    finish();
                }
                else
                {
                    //launch failed, show error
                    Toast.makeText(getApplicationContext(), "Could not launch Playback Activity!", Toast.LENGTH_LONG).show();
                }
            }
        }, splashDuration);
    }

    /**
     * Launch the playback activity with url and title set by the given intent's data
     * Intent is parsed and can be of type:
     * - ACTION_VIEW (open)
     * - ACTION_SEND (share)
     *
     * @param callingIntent the intent that opened the launch activity. used to parse playback url and title
     * @return if the playback activity was launched ok
     */
    private boolean launchPlayback(Intent callingIntent)
    {
        //dump the intent that called
        dumpIntent(callingIntent, "Calling Intent");

        //parse url and title
        Uri playbackUrl = parsePlaybackUrl(callingIntent);
        if (playbackUrl == null) return false;

        String title = parseTitle(playbackUrl, callingIntent);
        if (title.isEmpty()) return false;

        //construct intent for launching playback activity
        Intent launchIntent = new Intent(this, PlaybackActivity.class);
        launchIntent.setData(playbackUrl);
        launchIntent.putExtra(Intent.EXTRA_TITLE, title);

        //dump launch intent
        dumpIntent(launchIntent, "Launch Intent");

        //save the playback url as last played
        updateLastPlayedUrl(playbackUrl);

        //launch the playback activity
        startActivity(launchIntent);
        return true;
    }

    /**
     * Save the current url as last played url in shared prefs.
     * @param url the url to save
     */
    private void updateLastPlayedUrl(Uri url)
    {
        //get shared preferences
        SharedPreferences appPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        //set value
        appPreferences.edit().putString(ConfigKeys.KEY_DBG_LAST_PLAYED_URL, url.toString()).apply();
    }

    // region Intent Parsing

    /**
     * Dump the intents data to Logging.logd
     *
     * @param intent the intent to dump
     * @param desc   the description of the intent that is dumped
     */
    private void dumpIntent(Intent intent, String desc)
    {
        Logging.logD("========================================");
        Logging.logD("Dumping Intent " + desc);
        Logging.logD(intent.toString() + " of type " + intent.getType());
        Logging.logD("Data: " + intent.getData() + " (" + intent.getDataString() + ")");

        //dump extras
        Logging.logD("Extras: ");
        Bundle extras = intent.getExtras();
        if (extras != null)
        {
            for (String key : extras.keySet())
            {
                Logging.logD("  " + key + " = " + extras.get(key));
            }
        }
        else
        {
            Logging.logD("Intent has no extras.");
        }

        Logging.logD("========================================");
    }

    /**
     * Retrieve the Uri to play from a given intent
     *
     * @param intent the intent
     * @return the retried uri, or null if no uri was found
     */
    private Uri parsePlaybackUrl(Intent intent)
    {
        //log intent info
        Logging.logD("call Intent: %s", intent.toString());
        Bundle extra = intent.getExtras();
        if (extra != null)
        {
            Logging.logD("call Intent Extras: ");
            for (String key : extra.keySet())
            {
                Object val = extra.get(key);
                Logging.logD("\"%s\" : \"%s\"", key, (val == null ? "NULL" : val.toString()));
            }
        }

        //get playback uri from intent
        String action = intent.getAction();
        if (action == null || action.equalsIgnoreCase(Intent.ACTION_VIEW))
        {
            //action: open with OR directly open
            return intent.getData();
        }
        else if (action.equalsIgnoreCase(Intent.ACTION_SEND))
        {
            //action: send to
            String type = intent.getType();
            if (type == null) return null;

            if (type.equalsIgnoreCase("text/plain"))
            {
                //share a url from something like chrome, uri is in extra TEXT
                if (intent.hasExtra(Intent.EXTRA_TEXT))
                {
                    return Uri.parse(intent.getStringExtra(Intent.EXTRA_TEXT));
                }
            }
            else if (type.startsWith("video/")
                    || type.startsWith("audio/"))
            {
                //probably shared from gallery, uri is in extra STREAM
                if (intent.hasExtra(Intent.EXTRA_STREAM))
                {
                    return (Uri) intent.getExtras().get(Intent.EXTRA_STREAM);
                }
            }

            //failed to parse
            return null;
        }
        else
        {
            //unknown
            Logging.logW("Received Intent with unknown action: %s", intent.getAction());
            return null;
        }
    }

    /**
     * Parse the name of the streamed file from the playback uri
     *
     * @param uri          the playback uri (fallback to filename)
     * @param invokeIntent the intent that invoked this activity (parse Title Extra)
     * @return the parsed file name, or null if parsing failed
     */
    private String parseTitle(@NonNull Uri uri, @NonNull Intent invokeIntent)
    {
        //prep title
        String title = null;

        //get intent extras
        Bundle extraData = invokeIntent.getExtras();
        if (extraData != null)
        {
            //try to get title from extras
            if (extraData.containsKey(Intent.EXTRA_TITLE))
            {
                //has default title extra, use that
                title = extraData.getString(Intent.EXTRA_TITLE);
                Logging.logD("Parsing title from default EXTRA_TITLE...");
            }
            else
            {
                //check each key if it contains "title" and has a String value that is not null or empty
                for (String key : extraData.keySet())
                {
                    if (key.toLowerCase(Locale.US).contains("title"))
                    {
                        //key contains "title" in some sort, get value
                        Object val = extraData.get(key);

                        //check if value is not null and a string
                        if (val instanceof String)
                        {
                            //convert value to string
                            String valStr = (String) val;

                            //check if string value is not empty
                            if (!valStr.isEmpty())
                            {
                                //could be our title, set it
                                title = valStr;
                                Logging.logD("Parsing title from non- default title extra (\"%s\" : \"%s\")", key, valStr);
                            }
                        }
                    }
                }
            }


            if (title != null) Logging.logD("parsed final title from extra: %s", title);
        }

        //check if got title from extras
        if (title == null || title.isEmpty())
        {
            //no title set yet, try to get the title using the last path segment of the uri
            title = uri.getLastPathSegment();
            if (title != null && !title.isEmpty() && title.indexOf('.') != -1)
            {
                //last path segment worked, remove file extension
                title = title.substring(0, title.lastIndexOf('.'));
                Logging.logD("parse title from uri: %s", title);
            }
        }

        //return title
        return title;
    }
    // endregion
}
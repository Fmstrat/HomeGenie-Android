/*
    This file is part of HomeGenie for Adnroid.

    HomeGenie for Adnroid is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    HomeGenie for Adnroid is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with HomeGenie for Adnroid.  If not, see <http://www.gnu.org/licenses/>.
*/

/*
 *     Author: Generoso Martello <gene@homegenie.it>
 */

package com.glabs.homegenie;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.text.format.DateFormat;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.glabs.homegenie.adapters.GenericWidgetAdapter;
import com.glabs.homegenie.client.Control;
import com.glabs.homegenie.client.data.Event;
import com.glabs.homegenie.client.data.Group;
import com.glabs.homegenie.client.data.Module;
import com.glabs.homegenie.client.eventsource.EventSourceListener;
import com.glabs.homegenie.fragments.ErrorDialogFragment;
import com.glabs.homegenie.fragments.GroupsViewFragment;
import com.glabs.homegenie.fragments.MacroRecordDialogFragment;
import com.glabs.homegenie.fragments.SettingsFragment;
import com.glabs.homegenie.util.AsyncImageDownloadTask;
import com.glabs.homegenie.util.UpnpManager;
import com.glabs.homegenie.util.VoiceControl;
import com.glabs.homegenie.widgets.ModuleDialogFragment;

import java.util.ArrayList;


/**
 */
public class StartActivity extends FragmentActivity implements EventSourceListener {

    //variable for checking Voice Recognition support on user device
    private static final int VR_REQUEST = 1;

    public final String PREFS_NAME = "HomeGenieService";
    private GroupsViewFragment mGroupsViewFragment;
    private LinearLayout mLoadingCircle;
    private TextView mLoaderText;
    private TextView mDonateText;
    private TextView mEventText;
    private TextView mEventTime;
    private ImageView mEventIcon;

    private Handler mHandler;
    private Menu _actionmenu;
    private boolean _ispaused = false;
    public boolean _islogovisible = true;
    private VoiceControl _voicecontrol;
    private UpnpManager _upnpmanager;

    private Runnable mStatusChecker = new Runnable() {
        @Override
        public void run() {
            mGroupsViewFragment.UpdateCurrentGroupModules();
            Fragment widgetpopup = getSupportFragmentManager().findFragmentByTag("WIDGET");
            if (widgetpopup != null && widgetpopup instanceof ModuleDialogFragment) {
                ((ModuleDialogFragment) widgetpopup).refreshView();
            }
        }
    };

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Remove title bar
        //this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB && getActionBar() != null) {
            getActionBar().setDisplayShowHomeEnabled(false);
        }
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_start);

        mLoadingCircle = (LinearLayout) findViewById(R.id.loadingCircle);
        mLoaderText = (TextView) findViewById(R.id.tapoptions);
        mDonateText = (TextView) findViewById(R.id.donatetext);
        mEventText = (TextView) findViewById(R.id.eventStatus);
        mEventTime = (TextView) findViewById(R.id.eventTime);
        mEventIcon = (ImageView) findViewById(R.id.eventIcon);

        mGroupsViewFragment = new GroupsViewFragment();
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fm.beginTransaction();
        fragmentTransaction.add(R.id.fragmentMain, mGroupsViewFragment, "Groups");
        fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        fragmentTransaction.commit();
    }

    @Override
    public void onResume() {
        super.onResume();
        _ispaused = false;

        if (_upnpmanager == null) {
            _upnpmanager = new UpnpManager(this);
            _upnpmanager.bind();
        }

        // Connect to HomeGenie service
        if (Control.getGroups() == null && Control.getModules() == null) {
            homegenieConnect();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        _ispaused = true;

        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
        if (_upnpmanager != null) {
            _upnpmanager.unbind();
            _upnpmanager = null;
        }

        // Disconnect from HomeGenie service
        homegenieDisconnect();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        //outState.putString("WORKAROUND_FOR_BUG_19917_KEY", "WORKAROUND_FOR_BUG_19917_VALUE");
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
/*        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        else*/
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        homegenieDisconnect();
        super.onDestroy();
    }

    public void homegenieConnect() {
        // Reset groups fragment
        mGroupsViewFragment.setGroups(new ArrayList<Group>());
        // Read preferences, if empty let's show the settings dialog
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        if (settings.getString("serviceAddress", "127.0.0.1").equals("127.0.0.1")) {
            showLogo();
            showSettings();
        } else {
            loaderShow();
            if (mHandler != null) {
                mHandler.removeCallbacksAndMessages(null);
            }
            final StartActivity hgcontext = this;
            Control.setServer(
                    settings.getString("serviceAddress", "127.0.0.1"),
                    settings.getString("serviceUsername", "admin"),
                    settings.getString("servicePassword", ""),
                    settings.getBoolean("serviceSSL", false),
                    settings.getBoolean("serviceAcceptAll", false)
            );
            Control.connect(new Control.UpdateGroupsAndModulesCallback() {
                @Override
                public void groupsAndModulesUpdated(boolean success) {
                    if (success) {
                        mGroupsViewFragment.setGroups(Control.getGroups());
                        hideLogo();
                        //
                        if (_voicecontrol == null) {
                            _voicecontrol = new VoiceControl(hgcontext);
                        }
                    } else {
                        FragmentManager fm = getSupportFragmentManager();
                        if (!_ispaused) {
                            if (fm.findFragmentByTag("SETTINGS") == null || !fm.findFragmentByTag("SETTINGS").isVisible()) {
                                if (fm.findFragmentByTag("ERROR") == null || !fm.findFragmentByTag("ERROR").isVisible()) {
                                    ErrorDialogFragment fmWidget = new ErrorDialogFragment();
                                    FragmentTransaction fragmentTransaction = fm.beginTransaction();
                                    fragmentTransaction.add(fmWidget, "ERROR");
                                    fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
                                    fragmentTransaction.commit();
                                }
                            }
                        }
                    }
                    updateGroupModules();
                    loaderHide();
                }
            }, this);
        }
    }

    public void homegenieDisconnect() {
        Control.disconnect();
    }

    public void hideLogo() {
        _islogovisible = false;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Animation fadeOut = new AlphaAnimation(0.8f, 0);
                fadeOut.setInterpolator(new AccelerateInterpolator()); //and this
                fadeOut.setStartOffset(0);
                fadeOut.setDuration(500);
                //
                AnimationSet animation = new AnimationSet(false); //change to false
                animation.addAnimation(fadeOut);
                animation.setFillAfter(true);
                RelativeLayout ivlogo = (RelativeLayout) findViewById(R.id.logo);
                ivlogo.startAnimation(animation);
            }
        });
    }

    public void showLogo() {
        _islogovisible = true;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Animation fadeIn = new AlphaAnimation(0, 0.8f);
                fadeIn.setInterpolator(new AccelerateInterpolator()); //and this
                fadeIn.setStartOffset(0);
                fadeIn.setDuration(500);
                //
                AnimationSet animation = new AnimationSet(false); //change to false
                animation.addAnimation(fadeIn);
                animation.setFillAfter(true);
                RelativeLayout ivlogo = (RelativeLayout) findViewById(R.id.logo);
                ivlogo.startAnimation(animation);
            }
        });
    }

    public void loaderShow() {
        //mLoadingCircle.setAlpha(0.25f);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLoadingCircle.setVisibility(View.VISIBLE);
                mLoaderText.setText("Connecting to HomeGenie");
            }
        });
    }

    public void loaderHide() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mDonateText.setVisibility(View.GONE);
                mLoadingCircle.setVisibility(View.GONE);
                mLoaderText.setText("Tap Options menu for Settings");
            }
        });
    }

    public void showSettings() {
        SettingsFragment fmWidget = new SettingsFragment();
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fm.beginTransaction();
        fragmentTransaction.add(fmWidget, "SETTINGS");
        fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        fragmentTransaction.commitAllowingStateLoss();
        //fragmentTransaction.commit();
    }

    public void openMacroRecordMenu() {
        MacroRecordDialogFragment fmWidget = new MacroRecordDialogFragment();
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fm.beginTransaction();
        fragmentTransaction.add(fmWidget, "MACRORECORD");
        fragmentTransaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        fragmentTransaction.commit();
    }

    public void updateGroupModules() {
        if (Control.getGroups() != null && Control.getModules() != null) {
            if (mHandler != null) {
                mHandler.removeCallbacksAndMessages(null);
            }
            mHandler = new Handler();
            //startRepeatingTask();
            mHandler.postDelayed(mStatusChecker, 300);
        }
    }

    public void showOptionsMenu() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    openOptionsMenu();
                    if (_actionmenu != null) {
                        _actionmenu.performIdentifierAction(R.id.menu_system, 0);
                    }
                } catch (Exception e) {
                    showSettings();
                }
            }
        });
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (_actionmenu == null) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.menu_group, menu);
            _actionmenu = menu;
            //
            _actionmenu.findItem(R.id.action_recognition).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem menuItem) {
                    if (_voicecontrol != null)
                    {
                        _voicecontrol.startListen();
                    }
                    else
                    {
                        Toast.makeText(getApplicationContext(), "Unable to instantiate Voice Control.",
                                Toast.LENGTH_SHORT).show();
                    }
                    return true;
                }
            });

        }
        mGroupsViewFragment.UpdateCurrentGroupMenu();
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        Intent browserIntent;
        // Handle item selection
        switch (item.getItemId()) {

            case R.id.action_quit:
                finish();
                return true;

            case R.id.action_settings:
                showSettings();
                return true;

            case R.id.action_admin:
                // Read preferences
                SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
                String serviceAddress = settings.getString("serviceAddress", "");
                if (!serviceAddress.equals("")) {
                    browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://" + serviceAddress));
                    startActivity(browserIntent);
                }
                return true;

            case R.id.action_help:
                browserIntent = new Intent(this, WebActivity.class);
                browserIntent.putExtra("URL", Uri.parse("http://www.homegenie.it/docs").toString());
                startActivity(browserIntent);
                return true;

            //case R.id.action_refresh:
            //    return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onOptionsMenuClosed(Menu menu) {
        super.onOptionsMenuClosed(menu);
    }


    public Menu getActionMenu() {
        return _actionmenu;
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    @Override
    public void onSseConnect() {

    }

    @Override
    public void onSseEvent(final Event event) {

        if (event.Property.equals("Program.Status")) return;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateGroupModules();
                //
                String displayName = event.Domain + "." + event.Source;
                Module module = Control.getModule(event.Domain, event.Source);
                boolean filtered = isFilteredEvent(event);
                if (module != null && !filtered) {
                    final String imageUrl = Control.getHgBaseHttpAddress() + GenericWidgetAdapter.getModuleIcon(module);
                    if (mEventIcon.getTag() == null || !mEventIcon.getTag().equals(imageUrl) && !(mEventIcon.getDrawable() instanceof AsyncImageDownloadTask.DownloadedDrawable)) {
                        AsyncImageDownloadTask asyncDownloadTask = new AsyncImageDownloadTask(mEventIcon, true, new AsyncImageDownloadTask.ImageDownloadListener() {
                            @Override
                            public void imageDownloadFailed(String url) {
                            }

                            @Override
                            public void imageDownloaded(String url, Bitmap downloadedImage) {
                                mEventIcon.setTag(imageUrl);
                            }
                        });
                        asyncDownloadTask.download(imageUrl, mEventIcon);
                    }
                    displayName = module.getDisplayName() + " (" + module.getDisplayAddress() + ")";
                }
                //
                if (!filtered)
                {
                    mEventText.setText(displayName + "\n" + event.Property + " " + event.Value.replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n"));
                    mEventTime.setText(DateFormat.getTimeFormat(getApplicationContext()).format(event.Timestamp));
                }
            }
        });

    }

    @Override
    public void onSseError(String error) {

    }

    private boolean isFilteredEvent(Event event) {
        boolean isFiltered = false;
        if (event.Property.equals("Meter.Watts"))
        {
            try
            {
                double value = Double.parseDouble(event.Value);
                if (value == 0) isFiltered = true;
            } catch (Exception e) { }
        }
        return isFiltered;
    }

}

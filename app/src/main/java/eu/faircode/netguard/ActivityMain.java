package eu.faircode.netguard;

/*
    This file is part of NetGuard.

    NetGuard is free software: you can redistribute it and/or modify

    NetGuard is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with NetGuard.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2015-2019 by Marcel Bokhorst (M66B)
*/

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.net.VpnService;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import es.ugr.mdsm.amon.R;
import es.ugr.mdsm.restDump.DbDumper;

public class ActivityMain extends AppCompatActivity {
    private static final String TAG = "NetGuard.Main";

    private boolean running = false;
    //private ImageView ivIcon;
    //private ImageView ivQueue;
    private ImageButton imgSwitch;
    private boolean swEnabled;
    private Switch swAnonymize;
    //private ImageView ivMetered;
    private AlertDialog dialogFirst = null;
    private AlertDialog dialogVpn = null;
    private AlertDialog dialogDoze = null;
    //private AlertDialog dialogAbout = null;

    private DbDumper dbDumper;
    private static final int REQUEST_VPN = 1;
    private static final int REQUEST_LOGCAT = 2;
    public static final int REQUEST_ROAMING = 3;
    public static final int REQUEST_PCAP = 4;
    public static final int REQUEST_LOCATION = 5;
    public static final int INTERVAL_UPDATE = 20 * 1000;

    private static final int MIN_SDK = Build.VERSION_CODES.LOLLIPOP_MR1;

    public static final String ACTION_RULES_CHANGED = "eu.faircode.netguard.ACTION_RULES_CHANGED";
    public static final String ACTION_QUEUE_CHANGED = "eu.faircode.netguard.ACTION_QUEUE_CHANGED";
    public static final String EXTRA_REFRESH = "Refresh";
    public static final String EXTRA_SEARCH = "Search";
    public static final String EXTRA_RELATED = "Related";
    public static final String EXTRA_APPROVE = "Approve";
    public static final String EXTRA_LOGCAT = "Logcat";
    public static final String EXTRA_CONNECTED = "Connected";
    public static final String EXTRA_METERED = "Metered";
    public static final String EXTRA_SIZE = "Size";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "Create version=" + Util.getSelfVersionName(this) + "/" + Util.getSelfVersionCode(this));
        Util.logExtras(getIntent());

        // Check minimum Android version
        if (Build.VERSION.SDK_INT < MIN_SDK) {
            Log.i(TAG, "SDK=" + Build.VERSION.SDK_INT);
            super.onCreate(savedInstanceState);
            //setContentView(R.layout.android);
            return;
        }


        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        running = true;

        final SharedPreferences prefs = getSharedPreferences("Vpn", Context.MODE_PRIVATE);

        // Disable traffic lockdown
        lockNetwork(false);
        // Disable filtering
        logging(false);
        // Disable usage tracking
        trackUsage(false);
        // Enable flow collecting
        collectFlow(true);
        // Enable PCAP file
        // enablePcap(null);
        // Enable anoymization
        anonymizeData(false);
        // Compact flows
        compactFlow(false);

        prefs.edit().putBoolean("whitelist_other", false).apply();
        prefs.edit().putBoolean("whitelist_wifi", false).apply();

        /*
        findViewById(R.id.pcapButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportPcap();
            }
        });
        findViewById(R.id.pcapButton).setVisibility(View.INVISIBLE);
        */

        final boolean enabled = prefs.getBoolean("enabled", false);
        boolean anonymize = prefs.getBoolean("anonymizeApp", false);
        boolean initialized = prefs.getBoolean("initialized", false);

        // Upgrade - Modify some prefs, could be extracted.
        ReceiverAutostart.upgrade(initialized, this);
        //prefs.edit().putBoolean("lockdown_wifi",false).apply();
        //prefs.edit().putBoolean("lockdown_other",false).apply();
        dbDumper = new DbDumper(this);
        dbDumper.start();
        dbDumper.dumpAppInfo();
        dbDumper.dumpDeviceInfo();

        once();

        // Connection.getBondedDevicesByName();

        /*if (checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_LOCATION);
        } else { // Won't be on release
            Connection.isCurrentConnectionUnsecure(this);
        }*/

        // Debug switch
        imgSwitch = findViewById(R.id.swEnabled);
        swEnabled = enabled;
        updateSwitchImage();
        imgSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                swEnabled = !swEnabled;
                activateVpn(swEnabled);
            }
        });


        if (!getIntent().hasExtra(EXTRA_APPROVE)) {
            if (enabled)
                ServiceSinkhole.start("UI", this);
            else
                ServiceSinkhole.stop("UI", this, false);
        }

        if (enabled)
            checkDoze();

        // Listen for rule set changes
//        IntentFilter ifr = new IntentFilter(ACTION_RULES_CHANGED);
//        LocalBroadcastManager.getInstance(this).registerReceiver(onRulesChanged, ifr);

        // Listen for queue changes
//        IntentFilter ifq = new IntentFilter(ACTION_QUEUE_CHANGED);
//        LocalBroadcastManager.getInstance(this).registerReceiver(onQueueChanged, ifq);

        // Listen for added/removed applications
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        intentFilter.addDataScheme("package");
        registerReceiver(packageChangedReceiver, intentFilter);

        // First use
        if (!initialized) {
            // Create view
            LayoutInflater inflater = LayoutInflater.from(this);
            View view = inflater.inflate(R.layout.first, null, false);

            TextView tvFirst = view.findViewById(R.id.tvFirst);
            // TextView tvEula = view.findViewById(R.id.tvEula);
            // TextView tvPrivacy = view.findViewById(R.id.tvPrivacy);
            tvFirst.setMovementMethod(LinkMovementMethod.getInstance());
            // tvEula.setMovementMethod(LinkMovementMethod.getInstance());
            // tvPrivacy.setMovementMethod(LinkMovementMethod.getInstance());

            // Show dialog
            dialogFirst = new AlertDialog.Builder(this)
                    .setView(view)
                    .setCancelable(false)
                    .setPositiveButton(R.string.app_agree, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (running) {
                                prefs.edit().putBoolean("initialized", true).apply();
                            }
                        }
                    })
                    .setNegativeButton(R.string.app_disagree, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (running)
                                finish();
                        }
                    })
                    .setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialogInterface) {
                            dialogFirst = null;
                        }
                    })
                    .create();
            dialogFirst.show();


        }

        // Handle intent
        checkExtras(getIntent());
    }

    private void updateSwitchImage() {
        imgSwitch.setImageResource(swEnabled ? R.drawable.icon : R.drawable.icon_grayscale);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.i(TAG, "New intent");
        Util.logExtras(intent);
        super.onNewIntent(intent);

        if (Build.VERSION.SDK_INT < MIN_SDK || Util.hasXposed(this))
            return;

        setIntent(intent);
    }

    @Override
    protected void onResume() {
        Log.i(TAG, "Resume");

        if (Build.VERSION.SDK_INT < MIN_SDK || Util.hasXposed(this)) {
            super.onResume();
            return;
        }

        super.onResume();

        // Visual feedback
        SharedPreferences prefs = getSharedPreferences("Vpn", Context.MODE_PRIVATE);
        swEnabled = prefs.getBoolean("enabled", false);
        updateSwitchImage();
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "Pause");
        super.onPause();

        if (Build.VERSION.SDK_INT < MIN_SDK || Util.hasXposed(this))
            return;

    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.i(TAG, "Config");
        super.onConfigurationChanged(newConfig);

        if (Build.VERSION.SDK_INT < MIN_SDK || Util.hasXposed(this))
            return;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Destroy");

        if (Build.VERSION.SDK_INT < MIN_SDK || Util.hasXposed(this)) {
            super.onDestroy();
            return;
        }

        running = false;

        //getSharedPreferences("Vpn", Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(this);

//        LocalBroadcastManager.getInstance(this).unregisterReceiver(onRulesChanged);
//        LocalBroadcastManager.getInstance(this).unregisterReceiver(onQueueChanged);
        unregisterReceiver(packageChangedReceiver);

        if (dialogFirst != null) {
            dialogFirst.dismiss();
            dialogFirst = null;
        }
        if (dialogVpn != null) {
            dialogVpn.dismiss();
            dialogVpn = null;
        }
        if (dialogDoze != null) {
            dialogDoze.dismiss();
            dialogDoze = null;
        }

        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, final Intent data) {
        Log.i(TAG, "onActivityResult request=" + requestCode + " result=" + requestCode + " ok=" + (resultCode == RESULT_OK));
        Util.logExtras(data);

        if (requestCode == REQUEST_VPN) {
            // Handle Vpn approval
            SharedPreferences prefs = getSharedPreferences("Vpn", Context.MODE_PRIVATE);
            prefs.edit().putBoolean("enabled", resultCode == RESULT_OK).apply();
            swEnabled = resultCode == RESULT_OK;
            updateSwitchImage();
            prefs.edit().putBoolean("filter", true).apply();
            if (resultCode == RESULT_OK) {
                ServiceSinkhole.start("prepared", this);

                Toast on = Toast.makeText(ActivityMain.this, R.string.msg_on, Toast.LENGTH_LONG);
                on.setGravity(Gravity.CENTER, 0, 0);
                on.show();

                checkDoze();
            } else if (resultCode == RESULT_CANCELED)
                Toast.makeText(this, R.string.msg_vpn_cancelled, Toast.LENGTH_LONG).show();


        } else if (requestCode == REQUEST_LOGCAT) {
            // Send logcat by e-mail
            if (resultCode == RESULT_OK) {
                Uri target = data.getData();
                if (data.hasExtra("org.openintents.extra.DIR_PATH"))
                    target = Uri.parse(target + "/logcat.txt");
                Log.i(TAG, "Export URI=" + target);
                Util.sendLogcat(target, this);
            }
        } else if (requestCode == REQUEST_PCAP) {
            if (resultCode == RESULT_OK && data != null) {
                handleExportPCAP(data);
            }
        } else {
            Log.w(TAG, "Unknown activity result request=" + requestCode);
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_ROAMING) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                ServiceSinkhole.reload("permission granted", this, false);
        } else if (requestCode == REQUEST_LOCATION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    // TODO
                    //Connection.isCurrentConnectionUnsecure(this);
                }
            }
        }
    }

    private BroadcastReceiver onRulesChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received " + intent);
            Util.logExtras(intent);
        }
    };

    private BroadcastReceiver onQueueChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received " + intent);
            Util.logExtras(intent);
        }
    };

    private BroadcastReceiver packageChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "Received " + intent);
            Util.logExtras(intent);
        }
    };

    private void checkExtras(Intent intent) {
        // Approve request
        if (intent.hasExtra(EXTRA_APPROVE)) {
            Log.i(TAG, "Requesting Vpn approval");
            //swEnabled.toggle();
            activateVpn(!isVpnActivated());
        }

        if (intent.hasExtra(EXTRA_LOGCAT)) {
            Log.i(TAG, "Requesting logcat");
            Intent logcat = getIntentLogcat();
            if (logcat.resolveActivity(getPackageManager()) != null)
                startActivityForResult(logcat, REQUEST_LOGCAT);
        }
    }

    private void checkDoze() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final Intent doze = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            if (Util.batteryOptimizing(this) && getPackageManager().resolveActivity(doze, 0) != null) {
                final SharedPreferences prefs = getSharedPreferences("Vpn", Context.MODE_PRIVATE);
                if (!prefs.getBoolean("nodoze", false)) {
                    LayoutInflater inflater = LayoutInflater.from(this);
                    View view = inflater.inflate(R.layout.doze, null, false);
                    final CheckBox cbDontAsk = view.findViewById(R.id.cbDontAsk);
                    dialogDoze = new AlertDialog.Builder(this)
                            .setView(view)
                            .setCancelable(true)
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    prefs.edit().putBoolean("nodoze", cbDontAsk.isChecked()).apply();
                                    startActivity(doze);
                                }
                            })
                            .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    prefs.edit().putBoolean("nodoze", cbDontAsk.isChecked()).apply();
                                }
                            })
                            .setOnDismissListener(new DialogInterface.OnDismissListener() {
                                @Override
                                public void onDismiss(DialogInterface dialogInterface) {
                                    dialogDoze = null;
                                    checkDataSaving();
                                }
                            })
                            .create();
                    dialogDoze.show();
                } else
                    checkDataSaving();
            } else
                checkDataSaving();
        }
    }

    private void checkDataSaving() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            final Intent settings = new Intent(
                    Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            if (Util.dataSaving(this) && getPackageManager().resolveActivity(settings, 0) != null) {
                final SharedPreferences prefs = getSharedPreferences("Vpn", Context.MODE_PRIVATE);
                if (!prefs.getBoolean("nodata", false)) {
                    LayoutInflater inflater = LayoutInflater.from(this);
                    View view = inflater.inflate(R.layout.datasaving, null, false);
                    final CheckBox cbDontAsk = view.findViewById(R.id.cbDontAsk);
                    dialogDoze = new AlertDialog.Builder(this)
                            .setView(view)
                            .setCancelable(true)
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    prefs.edit().putBoolean("nodata", cbDontAsk.isChecked()).apply();
                                    startActivity(settings);
                                }
                            })
                            .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    prefs.edit().putBoolean("nodata", cbDontAsk.isChecked()).apply();
                                }
                            })
                            .setOnDismissListener(new DialogInterface.OnDismissListener() {
                                @Override
                                public void onDismiss(DialogInterface dialogInterface) {
                                    dialogDoze = null;
                                }
                            })
                            .create();
                    dialogDoze.show();
                }
            }
        }
    }

    private Intent getIntentLogcat() {
        Intent intent;
        intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "logcat.txt");
        return intent;
    }

    public void enablePcap(File file){
        SharedPreferences prefs = getSharedPreferences("Vpn", Context.MODE_PRIVATE);

        prefs.edit().putBoolean("pcap", true).apply();
        ServiceSinkhole.setPcap(true, file, this);
    }

    public void clearPcapLog(File file){
        final SharedPreferences prefs = getSharedPreferences("Vpn", Context.MODE_PRIVATE);
        final File pcap_file = file;

        new AsyncTask<Object, Object, Object>() {
            @Override
            protected Object doInBackground(Object... objects) {
                DatabaseHelper.getInstance(ActivityMain.this).clearLog(-1);
                if (prefs.getBoolean("pcap", false)) {
                    ServiceSinkhole.setPcap(false,pcap_file,ActivityMain.this);
                    if (pcap_file.exists() && !pcap_file.delete())
                        Log.w(TAG, "Delete PCAP failed");
                    ServiceSinkhole.setPcap(true,pcap_file,ActivityMain.this);
                } else {
                    if (pcap_file.exists() && !pcap_file.delete())
                        Log.w(TAG, "Delete PCAP failed");
                }
                return null;
            }

            @Override
            protected void onPostExecute(Object result) {
                if (running){
                    //updateAdapter();
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

    }

    public void exportPcap(){
        startActivityForResult(getIntentPCAPDocument(), REQUEST_PCAP);
    }

    private Intent getIntentPCAPDocument() {
        Intent intent;
        intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, "netguard_" + new SimpleDateFormat("yyyyMMdd").format(new Date().getTime()) + ".pcap");
        return intent;
    }

    private void handleExportPCAP(final Intent data) {
        new AsyncTask<Object, Object, Throwable>() {
            @Override
            protected Throwable doInBackground(Object... objects) {
                OutputStream out = null;
                FileInputStream in = null;
                try {
                    // Stop capture
                    ServiceSinkhole.setPcap(false,null, ActivityMain.this);

                    Uri target = data.getData();
                    if (data.hasExtra("org.openintents.extra.DIR_PATH"))
                        target = Uri.parse(target + "/netguard.pcap");
                    Log.i(TAG, "Export PCAP URI=" + target);
                    out = getContentResolver().openOutputStream(target);

                    File pcap = new File(getDir("data", MODE_PRIVATE), "netguard.pcap");
                    in = new FileInputStream(pcap);

                    int len;
                    long total = 0;
                    byte[] buf = new byte[4096];
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                        total += len;
                    }
                    Log.i(TAG, "Copied bytes=" + total);

                    return null;
                } catch (Throwable ex) {
                    Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                    return ex;
                } finally {
                    if (out != null)
                        try {
                            out.close();
                        } catch (IOException ex) {
                            Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                        }
                    if (in != null)
                        try {
                            in.close();
                        } catch (IOException ex) {
                            Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                        }

                    // Resume capture
                    SharedPreferences prefs = getSharedPreferences("Vpn", MODE_PRIVATE);
                    if (prefs.getBoolean("pcap", false))
                        ServiceSinkhole.setPcap(true,null, ActivityMain.this);
                }
            }

            @Override
            protected void onPostExecute(Throwable ex) {
                if (ex == null)
                    Toast.makeText(ActivityMain.this, R.string.msg_completed, Toast.LENGTH_LONG).show();
                else
                    Toast.makeText(ActivityMain.this, ex.toString(), Toast.LENGTH_LONG).show();
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    public boolean isVpnActivated(){
        SharedPreferences prefs = getSharedPreferences("Vpn", Context.MODE_PRIVATE);
        return prefs.getBoolean("enabled",false);
    }

    // Exported functionality from the previous switch.
    public void activateVpn(boolean enabled){
        final SharedPreferences prefs = getSharedPreferences("Vpn", Context.MODE_PRIVATE);
        if(enabled){
            String alwaysOn = Settings.Secure.getString(getContentResolver(), "always_on_vpn_app");
            Log.i(TAG, "Always-on=" + alwaysOn);
            if (!TextUtils.isEmpty(alwaysOn))
                if (getPackageName().equals(alwaysOn)) {
                    if (prefs.getBoolean("filter", false)) {
                        int lockdown = Settings.Secure.getInt(getContentResolver(), "always_on_vpn_lockdown", 0);
                        Log.i(TAG, "Lockdown=" + lockdown);
                        if (lockdown != 0) {
                            Toast.makeText(ActivityMain.this, R.string.msg_always_on_lockdown, Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                } else {
                    Toast.makeText(ActivityMain.this, R.string.msg_always_on, Toast.LENGTH_LONG).show();
                    return;
                }

            String dns_mode = Settings.Global.getString(getContentResolver(), "private_dns_mode");
            Log.i(TAG, "Private DNS mode=" + dns_mode);
            if (dns_mode == null)
                dns_mode = "off";
            if (!"off".equals(dns_mode)) {
                Toast.makeText(ActivityMain.this, R.string.msg_private_dns, Toast.LENGTH_LONG).show();
                return;
            }

            try {
                final Intent prepare = VpnService.prepare(ActivityMain.this);
                if (prepare == null) {
                    Log.i(TAG, "Prepare done");
                    onActivityResult(REQUEST_VPN, RESULT_OK, null);
                } else {
                    // Show dialog
                    LayoutInflater inflater = LayoutInflater.from(ActivityMain.this);
                    View view = inflater.inflate(R.layout.vpn, null, false);
                    dialogVpn = new AlertDialog.Builder(ActivityMain.this)
                            .setView(view)
                            .setCancelable(false)
                            .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if (running) {
                                        Log.i(TAG, "Start intent=" + prepare);
                                        try {
                                            // com.android.vpndialogs.ConfirmDialog required
                                            startActivityForResult(prepare, REQUEST_VPN);
                                        } catch (Throwable ex) {
                                            Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                                            onActivityResult(REQUEST_VPN, RESULT_CANCELED, null);
                                            swEnabled = false;
                                            updateSwitchImage();
                                            prefs.edit().putBoolean("enabled", false).apply();
                                        }
                                    }
                                }
                            })
                            .setOnDismissListener(new DialogInterface.OnDismissListener() {
                                @Override
                                public void onDismiss(DialogInterface dialogInterface) {
                                    dialogVpn = null;
                                }
                            })
                            .create();
                    dialogVpn.show();
                }
            } catch (Throwable ex) {
                // Prepare failed
                Log.e(TAG, ex.toString() + "\n" + Log.getStackTraceString(ex));
                swEnabled = false;
                updateSwitchImage();
                prefs.edit().putBoolean("enabled", false).apply();
            }

        } else{
            ServiceSinkhole.stop("switch off", ActivityMain.this, false);
            swEnabled = false;
            updateSwitchImage();
            prefs.edit().putBoolean("enabled", false).apply();
        }
    }

    private void once(){
        final SharedPreferences prefs = getSharedPreferences("Vpn", Context.MODE_PRIVATE);
        boolean initialized = prefs.getBoolean("initialized", false);

        if(!initialized){
            dbDumper.dumpDeviceInfo();
            dbDumper.dumpAppInfo();
            dbDumper.dumpSensorInfo();

            // new PremiumTelephonyChecker(this);

        }
    }

    public boolean isNetworkLocked(){
        SharedPreferences prefs = getSharedPreferences("Vpn", Context.MODE_PRIVATE);
        return prefs.getBoolean("lockdown",false);
    }

    public void lockNetwork(boolean locked){
        SharedPreferences prefs = getSharedPreferences("Vpn", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("lockdown",locked).apply();
        ServiceSinkhole.reload("changed lockdown", this, false);
        //WidgetLockdown.updateWidgets(this);
    }

    public boolean isLogging(){
        SharedPreferences prefs = getSharedPreferences("Vpn", Context.MODE_PRIVATE);
        return prefs.getBoolean("log",false);

    }

    public void logging(boolean enabled) {
        SharedPreferences prefs = getSharedPreferences("Vpn", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("log",enabled).apply();
        ServiceSinkhole.reload("changed logging", this, false);

    }

    public boolean isLoggingApp(){
        SharedPreferences prefs = getSharedPreferences("Vpn", Context.MODE_PRIVATE);
        return prefs.getBoolean("log_app",false);
    }

    public void appLogging(boolean enabled){
        SharedPreferences prefs = getSharedPreferences("Vpn", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("log_app",enabled).apply();
    }

    public boolean isUsageTracked(){
        SharedPreferences prefs = getSharedPreferences("Vpn", Context.MODE_PRIVATE);
        return prefs.getBoolean("track_usage",false) && isLoggingApp();
    }

    public void trackUsage(boolean enabled){
        SharedPreferences prefs = getSharedPreferences("Vpn", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("track_usage", enabled).apply();
        ServiceSinkhole.reload("changed tracking", this, false);
    }

    // Unused
    public Map<String, Boolean> loggedPackets(){
        Map<String, Boolean> booleanMap = new HashMap<>();
        SharedPreferences prefs = getSharedPreferences("Vpn", Context.MODE_PRIVATE);
        booleanMap.put("proto_udp",prefs.getBoolean("proto_udp",false));
        booleanMap.put("proto_tcp",prefs.getBoolean("proto_tcp",false));
        booleanMap.put("proto_other",prefs.getBoolean("proto_other",false));
        booleanMap.put("traffic_allowed",prefs.getBoolean("traffic_allowed",false));
        booleanMap.put("traffic_blocked",prefs.getBoolean("traffic_blocked",false));

        return booleanMap;
    }

    // Unused
    public void packetLogging(boolean udp, boolean tcp, boolean other,
                              boolean traffic_allowed, boolean traffic_blocked){
        SharedPreferences prefs = getSharedPreferences("Vpn", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("proto_udp",udp).apply();
        prefs.edit().putBoolean("proto_tcp",tcp).apply();
        prefs.edit().putBoolean("proto_other",other).apply();
        prefs.edit().putBoolean("traffic_allowed",traffic_allowed).apply();
        prefs.edit().putBoolean("traffic_blocked",traffic_blocked).apply();
        ServiceSinkhole.reload("changed packet logging", this, false);
    }

    public boolean isFlowCollected(){
        SharedPreferences prefs = getSharedPreferences("Vpn", Context.MODE_PRIVATE);
        return prefs.getBoolean("collect_flow",false);
    }

    public void collectFlow(boolean enabled){
        SharedPreferences prefs = getSharedPreferences("Vpn", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("collect_flow", enabled).apply();
        ServiceSinkhole.reload("changed flow capturing", this, false);
    }

    public boolean isDataAnonymized(){
        SharedPreferences prefs = getSharedPreferences("Vpn", Context.MODE_PRIVATE);
        return prefs.getBoolean("anonymizeApp",false);
    }

    public void anonymizeData(boolean enabled){
        SharedPreferences prefs = getSharedPreferences("Vpn", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("anonymizeApp", enabled).apply();
        ServiceSinkhole.reload("changed anonymization", this, false);
    }

    public boolean isFlowCompacted(){
        SharedPreferences prefs = getSharedPreferences("Vpn", Context.MODE_PRIVATE);
        return prefs.getBoolean("compactFlow",false);
    }

    public void compactFlow(boolean enabled){
        SharedPreferences prefs = getSharedPreferences("Vpn", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("compactFlow", enabled).apply();
        ServiceSinkhole.reload("changed flow compaction", this, false);
    }
}

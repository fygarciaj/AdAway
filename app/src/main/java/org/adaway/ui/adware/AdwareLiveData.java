package org.adaway.ui.adware;

import android.arch.lifecycle.LiveData;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.support.annotation.WorkerThread;

import org.adaway.util.AppExecutors;
import org.adaway.util.Constants;
import org.adaway.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class is {@link LiveData} to represents installed adware on device.
 */
class AdwareLiveData extends LiveData<List<AdwareInstall>> {
    /**
     * The adware package prefixes.
     */
    private static final String[] AD_PACKAGE_PREFIXES = {
            "com.airpush.",
            "com.adnotify.",
            "com.appbucks.sdk.",
            "com.appenda.",
            "com.applovin.",
            "com.iac.notification.",
            "com.inmobi.",
            "com.Leadbolt.",
            "com.sellaring.",
            "com.senddroid.",
            "com.tapjoy.",
            "cn.kuguo."
    };

    /**
     * The application context.
     */
    private final Context context;

    /**
     * Constructor.
     *
     * @param context The application context.
     */
    AdwareLiveData(Context context) {
        this.context = context;
        AppExecutors.getInstance().diskIO().execute(this::loadData);
    }

    @WorkerThread
    private void loadData() {
        // Get the package manager
        PackageManager pm = context.getPackageManager();
        // Get the adware packages
        List<PackageInfo> adwarePackages = this.getAdwarePackages(pm);
        // Create related adware installs
        List<AdwareInstall> adwareInstalls = new ArrayList<>(adwarePackages.size());
        for (PackageInfo pkg : adwarePackages) {
            // Retrieve application name
            String applicationName = pm.getApplicationLabel(pkg.applicationInfo).toString();
            // Add adware install
            adwareInstalls.add(new AdwareInstall(applicationName, pkg.packageName));
        }
        // Sort adware installs found
        Collections.sort(adwareInstalls);
        // Post loaded adware installs
        this.postValue(adwareInstalls);
    }

    /**
     * Finds all installed packages that look like they include a known ad framework
     *
     * @param pm The package manager.
     * @return The found adware package information.
     */
    private List<PackageInfo> getAdwarePackages(PackageManager pm) {
        List<PackageInfo> adPackages = new ArrayList<>();
        // It'd be simpler to just use pm.getInstalledPackages here, but apparently it's broken
        List<ApplicationInfo> applicationInfoList = pm.getInstalledApplications(0);
        for (ApplicationInfo applicationInfo : applicationInfoList) {
            try {
                // Retrieve package information
                PackageInfo packageInfo = pm.getPackageInfo(
                        applicationInfo.packageName,
                        PackageManager.GET_ACTIVITIES | PackageManager.GET_RECEIVERS | PackageManager.GET_SERVICES
                );
                if (this.isAdware(packageInfo)) {
                    adPackages.add(packageInfo);
                }
            } catch (Exception exception) {
                Log.e(Constants.TAG, "An error occurred while scanning applications for adware", exception);
            }
        }
        return adPackages;
    }

    /**
     * Check if application is an adware.
     *
     * @param info The application package information.
     * @return <code>true</code> if the appliaction is an adware, <code>false</code> otherwise.
     */
    private boolean isAdware(PackageInfo info) {
        // Get package name
        String packageName = info.packageName;
        Log.v(Constants.TAG, "Scanning package " + packageName);
        // Check package components
        boolean matchActivity = info.activities != null && checkComponent(packageName, "activity", info.activities);
        boolean matchReceiver = info.receivers != null && checkComponent(packageName, "receiver", info.receivers);
        boolean matchService = info.services != null && checkComponent(packageName, "service", info.services);
        return matchActivity || matchReceiver || matchService;
    }


    /**
     * Check if an application component match the adware signature.
     *
     * @param packageName The application package name.
     * @param type        The component type.
     * @param info        The application components to check.
     * @return <code>true</code> if a component matches adware signature, <code>false</code> otherwise.
     */
    private boolean checkComponent(String packageName, String type, ComponentInfo[] info) {
        for (ComponentInfo componentInfo : info) {
            String componentName = componentInfo.name;
            Log.v(Constants.TAG, "[" + type + "] " + componentName);
            for (String adPackagePrefix : AD_PACKAGE_PREFIXES) {
                if (componentName.startsWith(adPackagePrefix)) {
                    Log.i(Constants.TAG, String.format("Detected ad framework prefix %s in package %s as %s %s", adPackagePrefix, packageName, type, componentName));
                    return true;
                }
            }
        }
        return false;
    }
}

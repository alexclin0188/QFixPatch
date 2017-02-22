package alexclin.patch.qfix.tool;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.Application;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

public class PatchTool {
	private static final String TAG = "PatchTool";
	private static final String CLASS_ID_TXT = "class-ids.txt";
	private static final String DEFAULT_ENTRANCE = "Landroid/app/Application";
	
    public static final int CODE_RESOLVE_PATCH_ALL_SUCCESS = 0;
    public static final int CODE_JAVA_PARAMETER_ERROR = 1;
    public static final int CODE_NATIVE_INIT_PARAMETER_ERROR = 2;
    public static final int CODE_LOAD_DALVIK_SO_ERROR = 3;
    public static final int CODE_FIND_LOADED_CLASS_ERROR = 4;
    public static final int CODE_REFERRER_CLASS_OBJECT_ERROR = 5;
    public static final int CODE_RESOLVE_CLASS_ERROR = 6;
    public static final int CODE_NATIVE_ITEM_PARAMETER_ERROR = 7;
    public static final int CODE_PATCH_CLASS_OBJECT_ERROR = 8;
    public static final int NUM_FACTOR_PATCH = 10;

    public static native int nativeResolvePatchClass(String[] referrerClassList, long[] classIdxList, int size);

    private static boolean sIsLibLoaded = false;
    
    private static boolean loadPatchToolLib() {
		try {
			System.loadLibrary("ResolvePatch");
			return true;
		} catch (Throwable e) {
			Log.d(TAG, "loadPatchToolLib exception=" + e);
			return false;
		}
	}
    
    private static boolean resolvePatchClass(Application app, String[] referrerClassList, long[] classIdxList, int size) {
    	if (!sIsLibLoaded) {
			sIsLibLoaded = loadPatchToolLib();
		}
    	if (!sIsLibLoaded) {
			boolean unloadResult = InjectUtil.unloadPatchElement(app, 0);
			Log.e(TAG, "load lib failed, unload patch result=" + unloadResult);
			return false;
		} else {
			int resolveResult = nativeResolvePatchClass(referrerClassList, classIdxList, size);
			if (resolveResult != CODE_RESOLVE_PATCH_ALL_SUCCESS) {
				boolean unloadResult = InjectUtil.unloadPatchElement(app, 0);
				Log.e(TAG, String.format(Locale.ENGLISH,"resolve patch class failed, unload patch result= %b,refClass1:%s",
						unloadResult,referrerClassList[0]));
				return false;
			} else {
				Log.d(TAG, "resolve patch class success");
				return true;
			}
		}
    }

	public static void installPatch(Application application,File patchFile){
		installPatch(application,patchFile,DEFAULT_ENTRANCE,true);
	}

	public static boolean installPatch(Application application,File patchFile,String defaultEntranceClass,boolean checkSign){
		if(application==null||patchFile==null){
			Log.e(TAG,"Invalid parameters with context:"+application+",patchFile:"+patchFile+",entranceClass:"+defaultEntranceClass);
			return false;
		}
		if(!patchFile.exists()){
			Log.e(TAG,"PatchFile:"+patchFile+" not exists");
			return false;
		}
		if(checkSign&&!ApkChecker.verifyApk(application,patchFile)){
			Log.e(TAG,"Patch file signature is not same to current Application apk file, give up to install patch file:"+patchFile);
			return false;
		}
		List<Pair<String,Long>> classIds = readPatchClassIds(patchFile,defaultEntranceClass);
		if(classIds==null){
			Log.e(TAG,"readPatchClassIds return null list, drop to install patch");
			return false;
		}
		int size = classIds.size();
		if(size==0){
			Log.e(TAG,"no patch class-ids info in patch file:"+patchFile);
			return false;
		}
		String[] referrerClassList = new String[size];
		long[] classIdxList = new long[size];
		for(int i=0;i<size;i++){
			Pair<String,Long> item = classIds.get(i);
			referrerClassList[i] = item.first;
			classIdxList[i] = item.second;
		}
		boolean installSuc = InjectUtil.injectDex(application,patchFile);
        Log.d(TAG,"install patch result:"+installSuc);
		if(installSuc&&Build.VERSION.SDK_INT < 21){
			installSuc = resolvePatchClass(application,referrerClassList,classIdxList,size);
		}
		return installSuc;
	}

	private static List<Pair<String,Long>> readPatchClassIds(File patchFile, String defaultEntranceClass){
		List<Pair<String,Long>> classIds = new ArrayList<Pair<String,Long>>();
		InputStream inputStream = null;
		BufferedReader reader = null;
		SparseArray<String> dexEntrances = new SparseArray<String>();
		try {
			JarFile jarFile = new JarFile(patchFile);
			ZipEntry entry = jarFile.getEntry(CLASS_ID_TXT);
			inputStream = jarFile.getInputStream(entry);
			reader = new BufferedReader(new InputStreamReader(inputStream));
			String line;
			boolean isFirst = true;
			while ((line=reader.readLine())!=null){
				if(isFirst){
					isFirst = false;
					if(line.contains(":")){ //解析入口
						String[] entrances = line.split(":");
						for(int i=0;i<entrances.length;i++){
							String entrance = entrances[i];
							if(TextUtils.isEmpty(entrance))
								continue;
							dexEntrances.put(i+1,entrance);
						}
						boolean loadEntrance = loadEntranceClasses(dexEntrances);
						if(!loadEntrance){
							return null;
						}
						continue;
					}
				}
				if (!TextUtils.isEmpty(line)) {
					String[] infos = line.split("-");
					if (infos.length == 3) {
						long classId = Long.valueOf(infos[2]);
						int dexIndex = Integer.valueOf(infos[1]);
						String entrance = getEntranceClass(dexEntrances,dexIndex,defaultEntranceClass);
						classIds.add(Pair.create(entrance,classId));
					}
				}
			}
		} catch (Exception e) {
			Log.e(TAG,"readPatchClassIds",e);
			return null;
		} finally {
			try {
				if(reader!=null) reader.close();
			}catch (Exception e){
				Log.e(TAG,"readPatchClassIds",e);
			}
			try {
				if(inputStream!=null) inputStream.close();
			}catch (Exception e){
				Log.e(TAG,"readPatchClassIds",e);
			}
		}

		return classIds;
	}

	private static boolean loadEntranceClasses(SparseArray<String> dexEntrances) {
		for(int i=0;i<dexEntrances.size();i++){
			String className = dexEntrances.valueAt(i);
			try {
				className = className.substring(1,className.length()-1).replace("/",".");
				PatchTool.class.getClassLoader().loadClass(className);
			} catch (ClassNotFoundException e) {
				Log.e(TAG,String.format(Locale.ENGLISH,"load entrance class:%s,dexIndex:%d failed, return false to interrupt patch-loading",className,i));
				return false;
			}
		}
		return true;
	}

	private static String getEntranceClass(SparseArray<String> dexEntrances,int dexIndex,String defaultValue){
		if(dexEntrances.size()==0) return defaultValue;
		String entrance = dexEntrances.get(dexIndex);
		if(TextUtils.isEmpty(entrance)){
			entrance = defaultValue;
		}
		return entrance;
	}

    public static void restartDelayed(Context ctx, long timeInMills){
        final Intent intent = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        AlarmManager alarmManager = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = PendingIntent.getActivity(ctx,0,intent,PendingIntent.FLAG_UPDATE_CURRENT);
        alarmManager.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime()+timeInMills,pi);
    }

    public static void killSelfApp(Context ctx) {
        ActivityManager _ActivityManager = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> list = _ActivityManager.getRunningAppProcesses();
        for (ActivityManager.RunningAppProcessInfo info : list) {
            if (info.uid == android.os.Process.myUid() && info.pid != android.os.Process.myPid()) {
                android.os.Process.killProcess(info.pid);
            }
        }
        android.os.Process.killProcess(android.os.Process.myPid());
    }
}
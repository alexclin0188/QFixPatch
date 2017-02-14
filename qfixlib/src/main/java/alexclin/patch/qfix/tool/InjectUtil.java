package alexclin.patch.qfix.tool;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.zip.ZipFile;

import dalvik.system.DexFile;
import dalvik.system.PathClassLoader;

class InjectUtil {

	private static final String TAG = "InjectUtil";

	static boolean injectDex(Application context, File patchFile) {
        ArrayList<File> files = new ArrayList<>();
        files.add(patchFile);
        try {
			checkApkFiles(files);
            if(isAliyunOs()){
                for(File file:files){
                    injectLexFile(context,file);
                }
                return true;
            }else if(isAndroid()){
                installDex(context,InjectUtil.class.getClassLoader(),context.getDir("dex", 0),files);
                return true;
            }
            throw new IllegalStateException("Current system is not support");
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

	static boolean unloadPatchElement(Application app, int index) {
		if(isAliyunOs()){
			return unloadLexElement(app,index);
		}else if(isAndroid()){
			if(Build.VERSION.SDK_INT>= Build.VERSION_CODES.ICE_CREAM_SANDWICH){
				return unloadDexAboveEqualApiLevel14(app,index);
			}else{
				return unloadDexBelowApiLevel14(app,index);
			}
		}
		return false;
	}

	private static boolean isAndroid() {
		try {
			Class.forName("dalvik.system.BaseDexClassLoader");
			return true;
		} catch (ClassNotFoundException e) {
			return false;
		}
	}

	private static void checkApkFiles(List<File> files) throws IOException {
		for (File file:files){
			if(!file.exists()||!file.getName().endsWith(".apk")){
				throw new IOException("un support file:"+file);
			}
		}
	}

	private static void installDex(Application application, ClassLoader loader, File dexDir, List<File> files)
			throws Exception {
		if(!dexDir.exists()){
			dexDir.mkdirs();
		}
		if (!files.isEmpty()) {
			if (Build.VERSION.SDK_INT >= 24) {
				loader = AndroidNClassLoader.inject((PathClassLoader) loader, application);
			}

			if (Build.VERSION.SDK_INT >= 23) {
				V23.install(loader, files, dexDir);
			} else if (Build.VERSION.SDK_INT >= 19) {
				V19.install(loader, files, dexDir);
			} else if (Build.VERSION.SDK_INT >= 14) {
				V14.install(loader, files, dexDir);
			} else {
				V4.install(loader, files);
			}
		}
	}

	private static final class V23 {

		private static void install(ClassLoader loader, List<File> additionalClassPathEntries,
									File optimizedDirectory)
				throws IllegalArgumentException, IllegalAccessException,
				NoSuchFieldException, InvocationTargetException, NoSuchMethodException, IOException {
            /* The patched class loader is expected to be a descendant of
             * dalvik.system.BaseDexClassLoader. We modify its
             * dalvik.system.DexPathList pathList field to append additional DEX
             * file entries.
             */
			Object dexPathList = ReflectUtil.getField(loader, "pathList");
			ArrayList<IOException> suppressedExceptions = new ArrayList<IOException>();
            ReflectUtil.expandFieldArray(dexPathList, "dexElements", makePathElements(dexPathList,
					new ArrayList<File>(additionalClassPathEntries), optimizedDirectory,
					suppressedExceptions),true);
			if (suppressedExceptions.size() > 0) {
				for (IOException e : suppressedExceptions) {
					Log.w(TAG, "Exception in makePathElement", e);
					throw e;
				}
			}
		}

		/**
		 * A wrapper around
		 * {@code private static final dalvik.system.DexPathList#makePathElements}.
		 */
		private static Object[] makePathElements(
				Object dexPathList, ArrayList<File> files, File optimizedDirectory,
				ArrayList<IOException> suppressedExceptions)
				throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {

			Method makePathElements;
			try {
				makePathElements = ReflectUtil.findMethod(dexPathList, "makePathElements", List.class, File.class,
						List.class);
			} catch (NoSuchMethodException e) {
				Log.e(TAG, "NoSuchMethodException: makePathElements(List,File,List) failure");
				try {
					makePathElements = ReflectUtil.findMethod(dexPathList, "makePathElements", ArrayList.class, File.class, ArrayList.class);
				} catch (NoSuchMethodException e1) {
					Log.e(TAG, "NoSuchMethodException: makeDexElements(ArrayList,File,ArrayList) failure");
					try {
						Log.e(TAG, "NoSuchMethodException: try use v19 instead");
						return V19.makeDexElements(dexPathList, files, optimizedDirectory, suppressedExceptions);
					} catch (NoSuchMethodException e2) {
						Log.e(TAG, "NoSuchMethodException: makeDexElements(List,File,List) failure");
						throw e2;
					}
				}
			}

			return (Object[]) makePathElements.invoke(dexPathList, files, optimizedDirectory, suppressedExceptions);
		}
	}

	/**
	 * Installer for platform versions 19.
	 */
	private static final class V19 {

		private static void install(ClassLoader loader, List<File> additionalClassPathEntries,
									File optimizedDirectory)
				throws IllegalArgumentException, IllegalAccessException,
				NoSuchFieldException, InvocationTargetException, NoSuchMethodException {
            /* The patched class loader is expected to be a descendant of
             * dalvik.system.BaseDexClassLoader. We modify its
             * dalvik.system.DexPathList pathList field to append additional DEX
             * file entries.
             */
//			Field pathListField = ReflectUtil.findField(loader, "pathList");
//			Object dexPathList = pathListField.get(loader);
            Object dexPathList = ReflectUtil.getField(loader,"pathList");
			ArrayList<IOException> suppressedExceptions = new ArrayList<IOException>();
			Object[] extraElements = makeDexElements(dexPathList,
					new ArrayList<File>(additionalClassPathEntries), optimizedDirectory,
					suppressedExceptions);
            ReflectUtil.expandFieldArray(dexPathList, "dexElements", extraElements, true);
			if (suppressedExceptions.size() > 0) {
				for (IOException e : suppressedExceptions) {
					Log.e(TAG,"Exception in makeDexElement", e);
				}
                IOException[] dexElementsSuppressedExceptions = (IOException[])ReflectUtil.getField(loader,"dexElementsSuppressedExceptions");
				if (dexElementsSuppressedExceptions == null) {
					dexElementsSuppressedExceptions =
							suppressedExceptions.toArray(
									new IOException[suppressedExceptions.size()]);
				} else {
					IOException[] combined =
							new IOException[suppressedExceptions.size() +
									dexElementsSuppressedExceptions.length];
					suppressedExceptions.toArray(combined);
					System.arraycopy(dexElementsSuppressedExceptions, 0, combined,
							suppressedExceptions.size(), dexElementsSuppressedExceptions.length);
					dexElementsSuppressedExceptions = combined;
				}

                ReflectUtil.setField(loader,"dexElementsSuppressedExceptions",dexElementsSuppressedExceptions);
			}
		}

		/**
		 * A wrapper around
		 * {@code private static final dalvik.system.DexPathList#makeDexElements}.
		 */
		private static Object[] makeDexElements(
				Object dexPathList, ArrayList<File> files, File optimizedDirectory,
				ArrayList<IOException> suppressedExceptions)
				throws IllegalAccessException, InvocationTargetException,
				NoSuchMethodException {
			Method makeDexElements =
                    ReflectUtil.findMethod(dexPathList, "makeDexElements", ArrayList.class, File.class,
							ArrayList.class);

			return (Object[]) makeDexElements.invoke(dexPathList, files, optimizedDirectory,
					suppressedExceptions);
		}
	}

	/**
	 * Installer for platform versions 14, 15, 16, 17 and 18.
	 */
	private static final class V14 {
		private static void install(ClassLoader loader, List<File> additionalClassPathEntries,
									File optimizedDirectory)
				throws IllegalArgumentException, IllegalAccessException,
				NoSuchFieldException, InvocationTargetException, NoSuchMethodException {
            /* The patched class loader is expected to be a descendant of
             * dalvik.system.BaseDexClassLoader. We modify its
             * dalvik.system.DexPathList pathList field to append additional DEX
             * file entries.
             */
            Object dexPathList = ReflectUtil.getField(loader,"pathList");
            ReflectUtil.expandFieldArray(dexPathList, "dexElements", makeDexElements(dexPathList,
					new ArrayList<File>(additionalClassPathEntries), optimizedDirectory), true);
		}

		/**
		 * A wrapper around
		 * {@code private static final dalvik.system.DexPathList#makeDexElements}.
		 */
		private static Object[] makeDexElements(
				Object dexPathList, ArrayList<File> files, File optimizedDirectory)
				throws IllegalAccessException, InvocationTargetException,
				NoSuchMethodException {
			Method makeDexElements =
                    ReflectUtil.findMethod(dexPathList, "makeDexElements", ArrayList.class, File.class);

			return (Object[]) makeDexElements.invoke(dexPathList, files, optimizedDirectory);
		}
	}

	/**
	 * Installer for platform versions 4 to 13.
	 */
	private static final class V4 {
		//支持直接加载多个
		private static void install(ClassLoader loader, List<File> additionalClassPathEntries)
				throws IllegalArgumentException, IllegalAccessException,
				NoSuchFieldException, IOException {
            /* The patched class loader is expected to be a descendant of
             * dalvik.system.DexClassLoader. We modify its
             * fields mPaths, mFiles, mZips and mDexs to append additional DEX
             * file entries.
             */
			int extraSize = additionalClassPathEntries.size();

//			Field pathField = ReflectUtil.findField(loader, "path");

			StringBuilder path = new StringBuilder((String)ReflectUtil.getField(loader,"path"));
			String[] extraPaths = new String[extraSize];
			File[] extraFiles = new File[extraSize];
			ZipFile[] extraZips = new ZipFile[extraSize];
			DexFile[] extraDexs = new DexFile[extraSize];
			for (ListIterator<File> iterator = additionalClassPathEntries.listIterator();
				 iterator.hasNext(); ) {
				File additionalEntry = iterator.next();
				String entryPath = additionalEntry.getAbsolutePath();
				path.append(':').append(entryPath);
				int index = iterator.previousIndex();
				extraPaths[index] = entryPath;
				extraFiles[index] = additionalEntry;
				extraZips[index] = new ZipFile(additionalEntry);
				extraDexs[index] = DexFile.loadDex(entryPath, entryPath + ".dex", 0);
			}

            ReflectUtil.setField(loader,"path",path.toString());
            ReflectUtil.expandFieldArray(loader, "mPaths", extraPaths, true);
            ReflectUtil.expandFieldArray(loader, "mFiles", extraFiles, true);
            ReflectUtil.expandFieldArray(loader, "mZips", extraZips, true);
            ReflectUtil.expandFieldArray(loader, "mDexs", extraDexs, true);
		}
	}

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private static boolean unloadDexBelowApiLevel14(Context app, int index) {
		PathClassLoader pathClassLoader = (PathClassLoader) app.getClassLoader();
		try {
			ReflectUtil.setField(
					pathClassLoader,
					PathClassLoader.class,
					"mPaths",
					ReflectUtil.removeElementFromArray(ReflectUtil.getField(pathClassLoader, PathClassLoader.class, "mPaths"), index));
			ReflectUtil.setField(
					pathClassLoader,
					PathClassLoader.class,
					"mFiles",
					ReflectUtil.removeElementFromArray(ReflectUtil.getField(pathClassLoader, PathClassLoader.class, "mFiles"), index));
			ReflectUtil.setField(
					pathClassLoader,
					PathClassLoader.class,
					"mZips",
					ReflectUtil.removeElementFromArray(ReflectUtil.getField(pathClassLoader, PathClassLoader.class, "mZips"), index));
			ReflectUtil.setField(
					pathClassLoader,
					PathClassLoader.class,
					"mDexs",
					ReflectUtil.removeElementFromArray(ReflectUtil.getField(pathClassLoader, PathClassLoader.class, "mDexs"), index));
			return true;
		} catch (Throwable e) {
			e.printStackTrace();
			return false;
		}
	}

	@SuppressLint("NewApi")
	private static boolean unloadDexAboveEqualApiLevel14(Context app, int index) {
		PathClassLoader pathClassLoader = (PathClassLoader) app.getClassLoader();
		try {
			Object pathList = ReflectUtil.getField(pathClassLoader, Class.forName("dalvik.system.BaseDexClassLoader"), "pathList");
			Object dexElements = ReflectUtil.removeElementFromArray(ReflectUtil.getField(pathList,pathList.getClass(),"dexElements"), index);
			ReflectUtil.setField(pathList, pathList.getClass(), "dexElements", dexElements);
			return true;
		} catch (Throwable e) {
			e = null;
			return false;
		}
	}


    private static boolean isAliyunOs() {
        try {
            Class.forName("dalvik.system.LexClassLoader");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static void injectLexFile(Context ctx, File dexPath)
            throws ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException,
            InstantiationException, NoSuchFieldException {
        PathClassLoader obj = (PathClassLoader) InjectUtil.class.getClassLoader();
        String replaceAll = dexPath.getName().replaceAll("\\.[a-zA-Z0-9]+", ".lex");
        Class cls = Class.forName("dalvik.system.LexClassLoader");
        String ctxDexPath = ctx.getDir("dex", 0).getAbsolutePath();
        Object newInstance =
                cls.getConstructor(new Class[] {String.class, String.class, String.class, ClassLoader.class}).newInstance(
                        new Object[] {ctxDexPath+ File.separator + replaceAll,ctxDexPath, dexPath, obj});
        cls.getMethod("loadClass", new Class[] {String.class}).invoke(newInstance, new Object[] {"android.app.Application"});
        ReflectUtil.setField(obj, "mPaths",
                ReflectUtil.appendArray(ReflectUtil.getField(obj, "mPaths"), ReflectUtil.getField(newInstance, "mRawDexPath")));
        ReflectUtil.setField(obj, "mFiles",
                ReflectUtil.combineArray(ReflectUtil.getField(newInstance, "mFiles"), ReflectUtil.getField(obj, "mFiles")));
        ReflectUtil.setField(obj, "mZips",
                ReflectUtil.combineArray(ReflectUtil.getField(newInstance, "mZips"), ReflectUtil.getField(obj, "mZips")));
        ReflectUtil.setField(obj, "mLexs",
                ReflectUtil.combineArray(ReflectUtil.getField(newInstance, "mDexs"), ReflectUtil.getField(obj, "mLexs")));
    }

    private static boolean unloadLexElement(Context context, int index){
        PathClassLoader localClassLoader = (PathClassLoader) context.getClassLoader();

        try {
            ReflectUtil.setField(localClassLoader,
                    PathClassLoader.class,
                    "mPaths",
                    ReflectUtil.removeElementFromArray(ReflectUtil.getField(localClassLoader, PathClassLoader.class, "mPaths"), index));

            ReflectUtil.setField(
                    localClassLoader,
                    PathClassLoader.class,
                    "mFiles",
                    ReflectUtil.removeElementFromArray(ReflectUtil.getField(localClassLoader, PathClassLoader.class, "mFiles"), index));
            ReflectUtil.setField(
                    localClassLoader,
                    PathClassLoader.class,
                    "mZips",
                    ReflectUtil.removeElementFromArray(ReflectUtil.getField(localClassLoader, PathClassLoader.class, "mZips"), index));
            ReflectUtil.setField(
                    localClassLoader,
                    PathClassLoader.class,
                    "mLexs",
                    ReflectUtil.removeElementFromArray(ReflectUtil.getField(localClassLoader, PathClassLoader.class, "mLexs"), index));
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}

/*
 * CanHazLollipop
 * Copyright (C) 2014 Joseph C. Lehner
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of  MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package at.jclehner.canhazlollipop;

import java.util.Set;

import android.os.Build;
import android.util.TypedValue;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.Unhook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class CanHazLollipop implements IXposedHookLoadPackage, IXposedHookZygoteInit
{
	@Override
	public void initZygote(StartupParam startupParam) throws Throwable
	{
		XposedBridge.log("CanHazL: initZygote");
		fitsAndSits(ClassLoader.getSystemClassLoader());
	}

	@Override
	public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable
	{
		if(!"android".equals(lpparam.packageName))
			return;

		/*XposedBridge.log("CanHazL: handleLoadPackage");
		fitsAndSits(lpparam.classLoader);*/
	}

	private void fitsAndSits(final ClassLoader classLoader)
	{
		try
		{
			final Class<?> styleableClazz = XposedHelpers.findClass("com.android.internal.R.styleable",
					classLoader);

			final Class<?> packageParserClazz = XposedHelpers.findClass("android.content.pm.PackageParser",
					classLoader);

			/*
			final int[] usesSdkAttrs =
					(int[]) XposedHelpers.getStaticObjectField(styleableClazz, "AndroidManifestUsesSdk");

			XposedHelpers.findAndHookMethod("android.content.res.Resources", classLoader,
					"obtainAttributes", AttributeSet.class, int[].class, new XC_MethodHook() {

						@Override
						protected void afterHookedMethod(MethodHookParam param) throws Throwable
						{
							final int[] attrs = (int[]) param.args[1];
							if(attrs != usesSdkAttrs && !Arrays.equals(attrs, usesSdkAttrs))
								XposedHelpers.setAdditionalInstanceField(param.getResult(), "ignore", true);
							else
								XposedHelpers.setAdditionalInstanceField(param.getResult(), "ignore", false);
						}
			});
			*/


			final int targetSdkVersionAttr =
					XposedHelpers.getStaticIntField(styleableClazz, "AndroidManifestUsesSdk_targetSdkVersion");

			final int minSdkVersionAttr =
					XposedHelpers.getStaticIntField(styleableClazz, "AndroidManifestUsesSdk_minSdkVersion");


			final Set<Unhook> unhooks = XposedBridge.hookAllMethods(packageParserClazz, "parsePackage",
					new XC_MethodHook() {

						private Unhook mUnhook;


						@Override
						protected void beforeHookedMethod(final MethodHookParam outerParam) throws Throwable
						{
							// The actual signature of the version of parsePackage() we're interested in
							// has different signatures, at least on the kitkat-mr2.1-release and master
							// branches of today (2014-06-28), so we're using checking only the last
							// argument type (String[]). This is done because hooking the other variants
							// of parsePackage() as well causes the peekValue hook to be called for packages
							// not built against android-L (no idea why).

							if(!(outerParam.args[outerParam.args.length - 1] instanceof String[]))
							{
								mUnhook = null;
								return;
							}

							mUnhook = XposedHelpers.findAndHookMethod("android.content.res.TypedArray",
									classLoader, "peekValue", int.class,
									new XC_MethodHook() {

										@Override
										protected void afterHookedMethod(MethodHookParam param) throws Throwable
										{
											final TypedValue val = (TypedValue) param.getResult();
											if(val == null || val.type != TypedValue.TYPE_STRING || val.string == null)
												return;

											final int newData;

											final int resId = (Integer) param.args[0];
											if(resId == targetSdkVersionAttr)
												newData = 21;
											else if(resId == minSdkVersionAttr)
												newData = Build.VERSION.SDK_INT;
											else
												return;

											if("L".equals(val.string) || "android-L".equals(val.string))
											{
												final Object archiveSourcePath = getArchiveSourcePath(outerParam.thisObject);
												final String attrName = (resId == targetSdkVersionAttr ? "targetSdkVersion" :
													"minSdkVersion");
												final Object ignoreRaw = XposedHelpers.getAdditionalInstanceField(
														param.thisObject, "ignore");

												if(ignoreRaw != null && (Boolean) ignoreRaw)
												{
													XposedBridge.log("CanHazL: " + archiveSourcePath + ": not correcting " + attrName);
													return;
												}

												XposedBridge.log("CanHazL: " + archiveSourcePath + ": correcting " + attrName
														+  "='" + val.string + "' to " + newData
														+ "\n  val=" + val);

												final TypedValue newVal = new TypedValue();

												newVal.string = null;
												newVal.type = TypedValue.TYPE_INT_DEC;
												newVal.data = newData;

												param.setResult(newVal);
											}
										}
							});
						}

						@Override
						protected void afterHookedMethod(MethodHookParam param) throws Throwable
						{
							if(mUnhook != null)
								mUnhook.unhook();
						}
			});

			XposedBridge.log("CanHazL: hooked " + unhooks.size() + " parsePackage() functions");
		}
		catch(Throwable t)
		{
			XposedBridge.log(t);
		}
	}

	private Object getArchiveSourcePath(Object packageParser)
	{
		try
		{
			return XposedHelpers.getObjectField(packageParser,
					"mArchiveSourcePath");
		}
		catch(Throwable t)
		{
			XposedBridge.log(t);
			return "(unknown source path)";
		}
	}
}

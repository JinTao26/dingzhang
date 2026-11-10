# 只做"删掉没用到的东西"，不动自己的代码：
# 应用自身的类全部原样保留（不裁剪、不混淆），所以行为跟现在完全一致。
-keep class com.dingzhang.app.** { *; }

# 无障碍服务、通知监听、快捷方式这些由系统按名字启动的组件，名字不能变
-keep public class * extends android.accessibilityservice.AccessibilityService
-keep public class * extends android.service.notification.NotificationListenerService
-keep public class * extends android.content.BroadcastReceiver

# 库里的注解和泛型签名不参与裁剪
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

# 三方库的"可选依赖找不到"这类警告不必当错误
-dontwarn org.jetbrains.annotations.**
-dontwarn kotlinx.**

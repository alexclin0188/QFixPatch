package alexclin.qfix

import org.gradle.api.Project

class QFixExtension {
    HashSet<String> includePackage = []
    HashSet<String> excludeClass = []
    //补丁构建在debug版是否开启，默认开启
    boolean debugOn = false
    //补丁基础信息保存目录和补丁输出目录
    String outputDir
    //严格模式,启用则打包所有引用补丁类的class到补丁中，应对ART激进内联引起的问题
    boolean strictMode = false;

    QFixExtension(Project project) {
    }
}

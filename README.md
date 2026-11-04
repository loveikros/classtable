# 西柚课程表 · Offline Timetable

原生 Android 离线课程表，面向西柚研究生选课网页的课表导入场景。课表保存在本机，可手动维护，无需持续联网。

## 功能

- 多学期管理、当前学期切换及周次锚点校准，以周一为周次分界。
- 按周查看课程安排，支持同一时段不同适用周次的课程。
- 手动添加、编辑和删除课程安排，并标记手动修改。
- 指定日期的停课、补课及信息变更。
- 在 App 内登录学校网页、打开“我的课表”，抓取后预览导入；重新导入时处理与手动修改的冲突。
- 本地 JSON 备份和恢复，恢复前提供预览。
- 2×2 桌面小组件，展示当天正在上或接下来的课程。

## 构建

需要 JDK 17、Android SDK Platform 36，以及 Android 8.0（API 26）或更高版本的设备。使用 Android Studio 打开项目并配置 SDK，或在本机 `local.properties` 中设置 `sdk.dir`。不要提交该文件。

项目包含 Gradle 8.13 Wrapper，首次构建需要联网下载构建工具和依赖。

Windows：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

macOS / Linux：

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

调试安装包生成于 `app/build/outputs/apk/debug/app-debug.apk`。运行设备测试前，先连接设备或启动模拟器：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

## 使用

新建学期并设置周次锚点，再手动录入课程；或点击“从学校网页导入”，自行完成登录并打开“我的课表”，抓取并核对预览后保存。日期例外仅影响指定日期。

导入网页全屏显示，支持双指缩放、返回和刷新。首页出现登录表单后，可点“定位账号登录”放大并居中到学号输入区域；按提示登录、打开“我的课表”弹窗后，会出现“抓取课表并预览”按钮。

备份恢复会替换本机课表，请先导出已有数据。桌面小组件可通过 Android 桌面的“小组件”菜单添加。

## 隐私与限制

课表存储在本机 SQLite 中；联网权限用于学校网页导入。当前版本不提供保存或自动填入教务密码的功能，启动时会清除旧版凭据存储。WebView 可能保留网站 Cookie、缓存和站点存储，请在学校网页退出账号；卸载或清除应用数据会清除这些本地数据。

备份文件为未加密 JSON，可能包含班级、教师、地点和课程安排，请自行妥善保管。仓库仅包含匿名测试样本，不提供真实选课网页、账号、课表数据库、备份或本机配置。更多说明见 [PRIVACY.md](PRIVACY.md)。

学校登录及课表网页可能变化，真实账号登录、弹窗流程和不同桌面启动器的小组件刷新仍需实机验证。小组件允许一定刷新延迟。

## 项目结构

- `app/src/main/`：界面、课程模型、SQLite 存储、网页导入、备份和小组件。
- `app/src/test/`：周次计算和学期选项的单元测试。
- `app/src/androidTest/`：导入、课程操作、备份和小组件的设备测试。
- `GLOSSARY.md`：领域术语。
- `DESIGN.md`、`SPEC.md`：设计与规格，含规划功能，以源码和本说明为当前实现依据。
- `.scratch/`：本地 Markdown 规格与任务记录。

## 许可证

项目代码和自带日历图标使用 [MIT License](LICENSE)。Gradle Wrapper 及第三方依赖遵循各自许可证。项目由社区独立开发，与学校无官方隶属关系，未附带学校网站素材。

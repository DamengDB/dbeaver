# DBeaver

该仓库主要提供了支持达梦数据数据库的DBeaver的插件。

## 主要功能

支持的功能包括基本的表、视图、物化视图等信息的展示，另一方面增加了对于空间数据的图形化展示。

## 使用方法

#### 1、将两个jar包放入dbeaver\plugins目录下

#### 2、修改dbeaver\configuration\org.eclipse.equinox.simpleconfigurator\bundles.info文件

添加以下语句：

```
org.jkiss.dbeaver.ext.damengdb,版本号,plugins/org.jkiss.dbeaver.ext.dameng_版本号.jar,4,false
org.jkiss.dbeaver.ext.damengdb.ui,版本号,plugins/org.jkiss.dbeaver.ext.damengdb.ui_版本号.jar,4,false
```

例如，两个jar包为：

org.jkiss.dbeaver.ext.damengdb_1.0.1.202503281523.jar

org.jkiss.dbeaver.ext.damengdb.ui_1.0.1.202503281523.jar

则需要添加以下语句：

```
org.jkiss.dbeaver.ext.damengdb,1.0.1.202503281523,plugins/org.jkiss.dbeaver.ext.dameng_1.0.1.202503281523.jar,4,false
org.jkiss.dbeaver.ext.damengdb.ui,1.0.1.202503281523,plugins/org.jkiss.dbeaver.ext.damengdb.ui_1.0.1.202503281523.jar,4,false
```


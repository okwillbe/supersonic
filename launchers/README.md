# Launchers 启动器模块

该模块负责将 SuperSonic 的各个组件（Chat, Headless, Auth 等）组装在一起，并提供不同场景下的启动入口。

## 子模块说明

* **`standalone`**: **单机全量模式**。
  * 将所有功能（Chat BI + Headless BI）打包在一个应用中。
  * 适合开发测试及中小规模部署。
  * 包含最终打包（Assembly）逻辑。
* **`chat`**: **独立 Chat 模式**。
  * 仅启动 Chat BI 服务，适用于微服务架构下的独立部署。
  * 通常依赖远程的 Headless 服务。
* **`headless`**: **独立 Headless 模式**。
  * 仅启动语义层服务，作为统一的数据服务后端。
* **`common`**: 启动器共用的配置和引导类。

## 打包说明

本项目使用 `maven-assembly-plugin` 进行打包，详见 `standalone` 目录下的文档。

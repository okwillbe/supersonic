# Auth 认证与授权模块

该模块负责 SuperSonic 系统的用户认证（Authentication）与权限控制（Authorization）。

## 子模块说明

* **`api`**: 定义认证与授权的公共接口和数据模型（DTO）。
* **`authentication`**: 实现用户身份认证逻辑（如登录、Token 校验）。
* **`authorization`**: 实现细粒度的权限控制逻辑，支持数据集级、列级和行级权限管理。

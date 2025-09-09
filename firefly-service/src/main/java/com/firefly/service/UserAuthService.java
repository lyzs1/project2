package com.firefly.service;

import com.firefly.domain.auth.*;
import com.firefly.domain.constant.AuthRoleConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class UserAuthService {

    @Autowired
    private UserRoleService userRoleService;

    @Autowired
    private AuthRoleService authRoleService;

    public UserAuthorities getUserAuthorities(Long userId) {
        //多对多
        List<UserRole> userRoleList = userRoleService.getUserRoleByUserId(userId);
        Set<Long> roleIdSet = userRoleList.stream().map(UserRole :: getRoleId).collect(Collectors.toSet());

        //关联表查询，查询一次即可得到多个表数据
        //查询用户所有角色的所有操作权限(此权限主要与前端联动)
        List<AuthRoleElementOperation> roleElementOperationList = authRoleService.getRoleElementOperationsByRoleIds(roleIdSet);
        //查询用户所有角色的所有访问权限(此权限主要与前端联动)
        List<AuthRoleMenu> authRoleMenuList = authRoleService.getAuthRoleMenusByRoleIds(roleIdSet);
        UserAuthorities userAuthorities = new UserAuthorities();
        userAuthorities.setRoleElementOperationList(roleElementOperationList);
        userAuthorities.setRoleMenuList(authRoleMenuList);
        return userAuthorities;
    }

    public void addUserDefaultRole(Long id) {
        UserRole userRole = new UserRole();
        AuthRole role = authRoleService.getRoleByCode(AuthRoleConstant.ROLE_LV0);
        userRole.setUserId(id);
        userRole.setRoleId(role.getId());
        userRoleService.addUserRole(userRole);
    }
}

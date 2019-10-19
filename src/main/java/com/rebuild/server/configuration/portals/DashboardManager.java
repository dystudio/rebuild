/*
rebuild - Building your business-systems freely.
Copyright (C) 2018 devezhao <zhaofang123@gmail.com>

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package com.rebuild.server.configuration.portals;

import cn.devezhao.persist4j.Record;
import cn.devezhao.persist4j.engine.ID;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.rebuild.server.Application;
import com.rebuild.server.metadata.EntityHelper;
import com.rebuild.server.service.bizz.RoleService;
import com.rebuild.server.service.bizz.UserHelper;
import com.rebuild.utils.JSONUtils;

/**
 * 首页仪表盘
 * 
 * @author devezhao
 * @since 12/20/2018
 */
public class DashboardManager extends SharableManager<ID> {
	
	public static final DashboardManager instance = new DashboardManager();
	private DashboardManager() { }
	
	/**
	 * 获取可用面板
	 * 
	 * @param user
	 * @return
	 */
	public JSON getDashList(ID user) {
		ID detected = detectUseConfig(user, "DashboardConfig", null, null);
		// 没有就初始化一个
		if (detected == null) {
			Record record = EntityHelper.forNew(EntityHelper.DashboardConfig, user);
			record.setString("config", JSONUtils.EMPTY_ARRAY_STR);
			record.setString("title", UserHelper.isAdmin(user) ? "默认仪表盘" : "我的仪表盘");
			record.setString("shareTo", UserHelper.isAdmin(user) ? SHARE_ALL : SHARE_SELF);
			Application.getCommonService().create(record);
		}

		// TODO 仪表盘列表
		String sql = "select configId,title,config,createdBy,shareTo from DashboardConfig where ";
		if (UserHelper.isAdmin(user)) {
			sql += String.format("createdBy.roleId = '%s'", RoleService.ADMIN_ROLE.toLiteral());
		} else {
			sql += String.format("createdBy = '%s' or shareTo = 'ALL'", user.toLiteral());
		}
		sql += " order by title asc";
		Object[][] array = Application.createQueryNoFilter(sql).array();
		
		// 补充图表标题
		for (int i = 0; i < array.length; i++) {
			JSONArray charts = JSON.parseArray((String) array[i][2]);
			ChartManager.instance.richingCharts(charts);
			array[i][2] = charts;
			array[i][3] = isEditable(user, (ID) array[i][0]);
		}

		return (JSON) JSON.toJSON(array);
	}

	/**
	 * 是否允许修改
	 * 
	 * @param user
	 * @param dashid
	 * @return
	 */
	public boolean isEditable(ID user, ID dashid) {
		final String ckey = "DashboardOWN-" + dashid;
		ID createdBy = (ID) Application.getCommonCache().getx(ckey);
		if (createdBy == null) {
			Object[] dash = Application.createQueryNoFilter(
					"select createdBy from DashboardConfig where configId = ?")
					.setParameter(1, dashid)
					.unique();
			if (dash == null) {
				return false;
			}
			createdBy = (ID) dash[0];
		}
		
		if (UserHelper.isAdmin(user) && UserHelper.isAdmin(createdBy)) {
			return true;
		}
		return user.equals(createdBy);
	}
	
	@Override
	public void clean(ID cacheKey) {
		Application.getCommonCache().evict("DashboardOWN-" + cacheKey);
		Application.getCommonCache().evict("DashboardConfig-null-null");
	}
}

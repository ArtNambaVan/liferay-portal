<%--
/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */
--%>

<%@ include file="/init.jsp" %>

<liferay-frontend:management-bar
	disabled="<%= fragmentDisplayContext.isDisabledFragmentCollectionsManagementBar() %>"
	includeCheckBox="<%= true %>"
	searchContainerId="fragmentCollections"
>
	<liferay-frontend:management-bar-buttons>
		<liferay-frontend:management-bar-display-buttons
			displayViews='<%= new String[] {"icon"} %>'
			portletURL="<%= currentURLObj %>"
			selectedDisplayStyle="<%= fragmentDisplayContext.getDisplayStyle() %>"
		/>
	</liferay-frontend:management-bar-buttons>

	<liferay-frontend:management-bar-filters>
		<liferay-frontend:management-bar-navigation
			navigationKeys='<%= new String[] {"all"} %>'
			portletURL="<%= currentURLObj %>"
		/>

		<liferay-frontend:management-bar-sort
			orderByCol="<%= fragmentDisplayContext.getOrderByCol() %>"
			orderByType="<%= fragmentDisplayContext.getOrderByType() %>"
			orderColumns="<%= fragmentDisplayContext.getOrderColumns() %>"
			portletURL="<%= currentURLObj %>"
		/>

		<c:if test="<%= fragmentDisplayContext.isShowFragmentCollectionsSearch() %>">
			<portlet:renderURL var="portletURL">
				<portlet:param name="mvcRenderCommandName" value="/fragment/view" />
				<portlet:param name="redirect" value="<%= currentURL %>" />
				<portlet:param name="displayStyle" value="<%= fragmentDisplayContext.getDisplayStyle() %>" />
			</portlet:renderURL>

			<li>
				<aui:form action="<%= portletURL.toString() %>" method="post" name="fm1">
					<liferay-ui:input-search markupView="lexicon" />
				</aui:form>
			</li>
		</c:if>
	</liferay-frontend:management-bar-filters>

	<liferay-frontend:management-bar-action-buttons>
		<liferay-frontend:management-bar-button href="javascript:;" icon="import-export" id="exportSelectedFragmentCollections" label="export" />

		<liferay-frontend:management-bar-button href="javascript:;" icon="trash" id="deleteSelectedFragmentCollections" label="delete" />
	</liferay-frontend:management-bar-action-buttons>
</liferay-frontend:management-bar>

<aui:form cssClass="container-fluid-1280" name="fm">
	<liferay-ui:search-container
		id="fragmentCollections"
		searchContainer="<%= fragmentDisplayContext.getFragmentCollectionsSearchContainer() %>"
	>
		<liferay-ui:search-container-row
			className="com.liferay.fragment.model.FragmentCollection"
			keyProperty="fragmentCollectionId"
			modelVar="fragmentCollection"
		>
			<portlet:renderURL var="rowURL">
				<portlet:param name="mvcRenderCommandName" value="/fragment/view_fragment_entries" />
				<portlet:param name="fragmentCollectionId" value="<%= String.valueOf(fragmentCollection.getFragmentCollectionId()) %>" />
			</portlet:renderURL>

			<%
			row.setCssClass("entry-card lfr-asset-folder");
			%>

			<liferay-ui:search-container-column-text>
				<liferay-ui:search-container-column-text colspan="<%= 2 %>">
					<liferay-frontend:horizontal-card
						actionJsp="/fragment_collection_action.jsp"
						actionJspServletContext="<%= application %>"
						resultRow="<%= row %>"
						rowChecker="<%= searchContainer.getRowChecker() %>"
						text="<%= HtmlUtil.escape(fragmentCollection.getName()) %>"
						url="<%= rowURL.toString() %>"
					>
						<liferay-frontend:horizontal-card-col>
							<liferay-frontend:horizontal-card-icon
								icon="documents-and-media"
							/>
						</liferay-frontend:horizontal-card-col>
					</liferay-frontend:horizontal-card>
				</liferay-ui:search-container-column-text>
			</liferay-ui:search-container-column-text>
		</liferay-ui:search-container-row>

		<liferay-ui:search-iterator displayStyle="<%= fragmentItemDisplayContext.getDisplayStyle() %>" markupView="lexicon" />
	</liferay-ui:search-container>
</aui:form>
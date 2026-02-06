package net.chikach.intellijjj.log

import com.intellij.openapi.Disposable
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.ui.VcsLogColorManager
import com.intellij.vcs.log.ui.VcsLogUiImpl
import com.intellij.vcs.log.ui.filter.VcsLogClassicFilterUi
import com.intellij.vcs.log.ui.filter.VcsLogFilterUiEx
import com.intellij.vcs.log.visible.VisiblePackRefresher
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject

class JujutsuVcsLogUiImpl(
    id: String,
    logData: VcsLogData,
    colorManager: VcsLogColorManager,
    uiProperties: MainVcsLogUiProperties,
    refresher: VisiblePackRefresher,
    initialFilters: VcsLogFilterCollection?
) : VcsLogUiImpl(id, logData, colorManager, uiProperties, refresher, initialFilters) {
    override fun createFilterUi(
        filterConsumer: java.util.function.Consumer<VcsLogFilterCollection>,
        filters: VcsLogFilterCollection?,
        parentDisposable: Disposable
    ): VcsLogFilterUiEx {
        return JujutsuVcsLogFilterUi(myLogData, filterConsumer, properties, myColorManager, filters, parentDisposable)
    }
}

private class JujutsuVcsLogFilterUi(
    logData: VcsLogData,
    filterConsumer: java.util.function.Consumer<VcsLogFilterCollection>,
    uiProperties: MainVcsLogUiProperties,
    colorManager: VcsLogColorManager,
    filters: VcsLogFilterCollection?,
    parentDisposable: Disposable
) : VcsLogClassicFilterUi(logData, filterConsumer, uiProperties, colorManager, filters, parentDisposable) {
    override fun createActionGroup(): com.intellij.openapi.actionSystem.ActionGroup {
        val actions = listOfNotNull(
            createUserComponent(),
            createDateComponent(),
            createStructureFilterComponent(),
            createGraphComponent()
        )
        return com.intellij.openapi.actionSystem.DefaultActionGroup(actions)
    }

    override fun getFilters(): VcsLogFilterCollection {
        val filters = buildList {
            addAll(textFilterModel.filtersList)
            addAll(structureFilterModel.filtersList)
            add(dateFilterModel.getFilter())
            add(userFilterModel.getFilter())
            add(parentFilterModel.getFilter())
        }.filterNotNull()
        return VcsLogFilterObject.collection(*filters.toTypedArray())
    }

    override fun setFilters(collection: VcsLogFilterCollection) {
        structureFilterModel.setFilter(collection)
        textFilterModel.setFilter(collection)
        dateFilterModel.setFilter(collection.get(VcsLogFilterCollection.DATE_FILTER))
        userFilterModel.setFilter(collection.get(VcsLogFilterCollection.USER_FILTER))
        parentFilterModel.setFilter(collection.get(VcsLogFilterCollection.PARENT_FILTER))
    }
}

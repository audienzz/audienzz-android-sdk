package org.audienzz.mobile.event.id

import org.audienzz.mobile.AudienzzPrebidMobile
import javax.inject.Inject

class CompanyIdProviderImpl @Inject constructor() : CompanyIdProvider {

    override fun getCompanyId() = AudienzzPrebidMobile.companyId
}

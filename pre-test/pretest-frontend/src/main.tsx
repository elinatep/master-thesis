import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import '@insurance-portal/core/theme.scss'
import 'bootstrap-icons/font/bootstrap-icons.css'
import '@insurance-portal/core/index.css'
import { App } from '@insurance-portal/core'
import { configurePortalHost } from '@insurance-portal/core/host'
import StudyGate from './study/StudyGate.tsx'
import NavigationBridge from './study/NavigationBridge.tsx'
import StudyChrome from './study/StudyChrome.tsx'
import DataPage from './study/DataPage.tsx'
import OutcomeScreen from './pretest/OutcomeScreen.tsx'
import { OUTCOME_PATH, isClaimOutcomeEvent } from './pretest/outcomeRoute.ts'
import { participantHeaders } from './study/headers'
import { getCondition } from './study/session'
import { goTo } from './study/navigation'
import { logEvent } from './study/log'

/*
 * The participant uses the real Salvena portal. They land on the dashboard, find their way to the
 * claim form, choose a policy and file the claim there - so the effort they put in, and the portal
 * they put it into, are the genuine article rather than a mock of one.
 *
 * The study only takes over at the moment the portal reports what happened to the submission. What
 * Salvena then says about the claim is the manipulation, and it is not the portal's business: the
 * portal's claim engine models claims correctly and would never approve 5% of one without
 * explanation. That screen is the study's, and its words come from configuration/arms/.
 *
 * Every cell of the design uses the identical portal. Only the outcome differs.
 */

configurePortalHost({
  apiHeaders: participantHeaders,
  accountRef: () => getCondition()?.participantId ?? null,
  interactionMode: () => 'interactive',
  navbarChrome: () => <StudyChrome />,
  onEvent: (type, data) => {
    // Log first, unconditionally. The takeover below changes the route, and an event lost to a
    // navigation is an unrecoverable data point.
    logEvent(type, data)
    if (isClaimOutcomeEvent(type, data)) goTo(OUTCOME_PATH)
  },
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <NavigationBridge />
      {/*
        The study's own routes sit above the portal's. /data is the researcher's behavioural export
        and is outside the gate on purpose: opening it must not create a participant session.
      */}
      {/*
        One gate, above the routes rather than inside them. The participant moves between the
        portal and the study's outcome screen during a run, and a gate wrapped around each route
        would unmount and remount on every move - re-running intake and opening a second session
        for the same run. /data is the researcher's export and is exempted inside the gate, so it
        renders without a participant session ever being created.
      */}
      <StudyGate>
        <Routes>
          <Route path="/data" element={<DataPage />} />
          <Route path={OUTCOME_PATH} element={<OutcomeScreen />} />
          <Route path="*" element={<App />} />
        </Routes>
      </StudyGate>
    </BrowserRouter>
  </StrictMode>,
)

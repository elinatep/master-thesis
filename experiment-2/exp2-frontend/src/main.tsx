import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import '@insurance-portal/core/theme.scss'
import 'bootstrap-icons/font/bootstrap-icons.css'
import '@insurance-portal/core/index.css'
import { App } from '@insurance-portal/core'
import StudyGate from './study/StudyGate.tsx'
import StudyChrome from './study/StudyChrome.tsx'
import DataPage from './study/DataPage.tsx'
import StudySplit from './study/StudySplit.tsx'
import VoiceWidget from './voice/VoiceWidget'
import { configurePortalHost } from '@insurance-portal/core/host'
import { participantHeaders } from './study/headers'
import { getCondition } from './study/session'
import { logEvent } from './study/log'

/*
 * Experiment 2 does not vary how the participant uses the portal — every cell of the 2x2 gets the
 * same interactive portal and the same voice assistant. Both manipulations happen inside the call:
 * whether the AI acknowledges how the participant feels, and whether the human employee does after
 * the handover. So there is no interaction-mode switch here, and no headless view: the portal's
 * readonly/headless modes exist for levels-of-ai-help, where the bot drives or replaces the GUI.
 *
 * Keeping the portal identical across cells is what makes the design clean — the only thing that
 * differs between participants is what they are told on the phone.
 */

// Composition root: the study hosts the portal. Everything study-specific the portal needs is
// handed over here, so no portal module imports study/ directly. Configured before render; every
// entry is a function, evaluated when the portal asks, so the condition only has to be resolved by
// the time the portal renders (which StudyGate guarantees) rather than right now.
configurePortalHost({
  apiHeaders: participantHeaders,
  accountRef: () => getCondition()?.participantId ?? null,
  interactionMode: () => 'interactive',
  onEvent: logEvent,
  navbarChrome: () => <StudyChrome />,
  // Every cell has the voice assistant. What differs is what it says, not whether it is there.
  assistant: () => <VoiceWidget />,
})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      {/*
        The study's own routes sit above the portal's. /data is the researcher's behavioural export
        — study data, study page — so it is served here rather than being a portal route that
        happens to call a study endpoint. It is outside StudyGate on purpose: opening it must not
        create a participant session.
      */}
      <Routes>
        <Route path="/data" element={<DataPage />} />
        <Route
          path="*"
          element={
            <StudyGate>
              {/*
                The task card sits beside the portal in every cell. Inside the gate, so it appears
                only once a condition is resolved - and never on /data, which is routed above and is
                the researcher's page, not a participant's.
              */}
              <StudySplit>
                <App />
              </StudySplit>
            </StudyGate>
          }
        />
      </Routes>
    </BrowserRouter>
  </StrictMode>,
)

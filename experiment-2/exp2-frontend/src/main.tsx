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
import { configureFormat, configurePortalHost } from '@insurance-portal/core/host'
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

// The portal's own pages - policies, claims, the dashboard - format money and dates themselves.
// Left alone they use the library's Swiss defaults, so a British Prolific participant would read
// "CHF 1'200.00" and "14.03.2026" on the website of the insurer that is about to pay them in
// pounds. The amounts the study itself names (1,200 claimed, 1,080 expected, 60 approved) are in
// the survey and the voice instructions; this is what the portal says around them.
//
// The backend has the other half - portal.format.currency in study.properties - and the two have
// to be set together. A policy certificate that says CHF, downloaded from a page that said GBP, is
// the contradiction most likely to be noticed, because it is the artefact somebody looks at twice.
configureFormat({ currency: 'GBP', groupSeparator: ',', dateSeparator: '/' })

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

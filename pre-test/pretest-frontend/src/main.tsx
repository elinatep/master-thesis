import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import '@insurance-portal/core/theme.scss'
import 'bootstrap-icons/font/bootstrap-icons.css'
import '@insurance-portal/core/index.css'
import StudyGate from './study/StudyGate.tsx'
import DataPage from './study/DataPage.tsx'
import ClaimFlow from './pretest/ClaimFlow.tsx'

/*
 * The pre-test mounts its own flow rather than the portal application.
 *
 * The portal library is still what makes this look like Salvena - its theme and components are
 * imported above - but none of its pages are rendered. The pre-test's four arms need a portal that
 * approves 5% of a claim without explanation, or fails twice with the same error code; the portal's
 * claims domain would do neither, and asking it to pretend would mean teaching the real portal to
 * lie. The scripted flow lives in the study application instead, and the portal stays honest.
 *
 * Experiment 2 is where the portal proper gets used: there the participant really does review a
 * claim, navigate, and act.
 */

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      {/*
        /data is the researcher's behavioural export - study data, study page - and sits outside
        the gate on purpose: opening it must not create a participant session.
      */}
      <Routes>
        <Route path="/data" element={<DataPage />} />
        <Route
          path="*"
          element={
            <StudyGate>
              <ClaimFlow />
            </StudyGate>
          }
        />
      </Routes>
    </BrowserRouter>
  </StrictMode>,
)

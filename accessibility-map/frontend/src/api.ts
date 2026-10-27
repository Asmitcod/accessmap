import type { CreateReport, FeatureType, Place, ReportStatus } from './types'

const API_BASE = import.meta.env.VITE_API_BASE || '/api'
export const publicAuthEnabled = import.meta.env.VITE_PUBLIC_AUTH_ENABLED !== 'false'
export const publicContributionsEnabled = import.meta.env.VITE_PUBLIC_CONTRIBUTIONS_ENABLED !== 'false'

async function responseErrorMessage(response: Response, fallback: string): Promise<string> {
  const body = await response.text()
  if (response.headers.get('content-type')?.includes('json') && body) {
    const problem = JSON.parse(body) as { detail?: unknown }
    if (typeof problem.detail === 'string') return problem.detail
  }
  return body || fallback
}

export interface AuthResult {
  token: string
  email: string
  expiresInSeconds: number
  moderator: boolean
}

export interface ModerationReport {
  id: number
  placeName: string
  featureLabel: string
  featureType: FeatureType
  status: ReportStatus
  note: string | null
  reporterEmail: string | null
  reportedAt: string
}

export interface ModerationAuditEntry {
  reportId: number
  placeName: string
  featureLabel: string
  featureType: FeatureType
  reportStatus: ReportStatus
  decision: 'APPROVED' | 'REJECTED'
  moderatorEmail: string
  reviewedAt: string
}

export async function authenticate(
  mode: 'login' | 'register',
  email: string,
  password: string,
): Promise<AuthResult> {
  const response = await fetch(`${API_BASE}/auth/${mode}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  })
  if (!response.ok) {
    throw new Error(await responseErrorMessage(
      response,
      `Could not ${mode === 'login' ? 'log in' : 'register'} (${response.status}).`,
    ))
  }
  return response.json() as Promise<AuthResult>
}

export async function getPlaces(search = ''): Promise<Place[]> {
  const params = new URLSearchParams()
  if (search.trim()) params.set('search', search.trim())

  const response = await fetch(`${API_BASE}/places?${params.toString()}`)
  if (!response.ok) throw new Error(`Could not load places (${response.status}).`)
  return response.json() as Promise<Place[]>
}

export async function createReport(
  placeId: string,
  featureId: string,
  report: CreateReport,
  token: string,
): Promise<void> {
  const response = await fetch(
    `${API_BASE}/places/${encodeURIComponent(placeId)}/features/${encodeURIComponent(featureId)}/reports`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
      body: JSON.stringify(report),
    },
  )

  if (!response.ok) {
    throw new Error(await responseErrorMessage(response, `Could not submit report (${response.status}).`))
  }
}

export async function createFeatureReport(
  placeId: string,
  token: string,
  report: {
    type: FeatureType
    label: string
    status: ReportStatus
    note: string
  },
): Promise<void> {
  const response = await fetch(`${API_BASE}/places/${encodeURIComponent(placeId)}/features`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify(report),
  })

  if (!response.ok) {
    throw new Error(await responseErrorMessage(response, `Could not add feature (${response.status}).`))
  }
}

export async function getPendingReports(token: string): Promise<ModerationReport[]> {
  const response = await fetch(`${API_BASE}/moderation/reports`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!response.ok) {
    throw new Error(await responseErrorMessage(response, `Could not load moderation queue (${response.status}).`))
  }
  return response.json() as Promise<ModerationReport[]>
}

export async function getModerationAudit(token: string): Promise<ModerationAuditEntry[]> {
  const response = await fetch(`${API_BASE}/moderation/reports/audit`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!response.ok) {
    throw new Error(await responseErrorMessage(response, `Could not load moderation history (${response.status}).`))
  }
  return response.json() as Promise<ModerationAuditEntry[]>
}

export async function reviewReport(
  reportId: number,
  decision: 'APPROVE' | 'REJECT',
  token: string,
): Promise<void> {
  const response = await fetch(`${API_BASE}/moderation/reports/${reportId}/decision`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ decision }),
  })
  if (!response.ok) {
    throw new Error(await responseErrorMessage(response, `Could not review report (${response.status}).`))
  }
}

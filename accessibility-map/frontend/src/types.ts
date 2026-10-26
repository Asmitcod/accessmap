export type FeatureType = 'ENTRANCE' | 'LIFT' | 'RAMP' | 'RESTROOM'

export type ReportStatus =
  | 'STEP_FREE'
  | 'LIMITED'
  | 'BLOCKED'
  | 'WORKING'
  | 'INTERMITTENT'
  | 'OUT_OF_SERVICE'
  | 'GENTLE'
  | 'MODERATE'
  | 'STEEP'
  | 'VERY_STEEP'
  | 'USABLE'
  | 'LOCKED'
  | 'UNKNOWN'

export interface FeatureSummary {
  id: string
  type: FeatureType
  label: string
  status: ReportStatus
  lastConfirmedAt: string | null
  confirmationCount: number
}

export interface Place {
  id: string
  name: string
  address: string
  category: string
  latitude: number
  longitude: number
  demoData: boolean
  features: FeatureSummary[]
}

export interface CreateReport {
  status: ReportStatus
  note: string
}

import { useEffect, useMemo, useState } from 'react'
import type { FormEvent } from 'react'
import {
  AlertCircle,
  ArrowUpRight,
  Check,
  ChevronDown,
  Clock3,
  DoorOpen,
  LocateFixed,
  MapPin,
  Menu,
  Plus,
  Search,
  ShieldCheck,
  SlidersHorizontal,
  Sparkles,
  Toilet,
  TrainFront,
  X,
} from 'lucide-react'
import {
  CircleMarker,
  MapContainer,
  TileLayer,
  ZoomControl,
  useMap,
} from 'react-leaflet'
import {
  authenticate,
  createFeatureReport,
  createReport,
  getModerationAudit,
  getPendingReports,
  getPlaces,
  publicAuthEnabled,
  publicContributionsEnabled,
  reviewReport,
} from './api'
import type { ModerationAuditEntry, ModerationReport } from './api'
import { demoPlaces } from './demo-data'
import type { FeatureSummary, FeatureType, Place, ReportStatus } from './types'

const featureLabels: Record<FeatureType, string> = {
  ENTRANCE: 'Entrance',
  LIFT: 'Lift',
  RAMP: 'Ramp',
  RESTROOM: 'Restroom',
}

const featureIcons = {
  ENTRANCE: DoorOpen,
  LIFT: TrainFront,
  RAMP: ArrowUpRight,
  RESTROOM: Toilet,
}

const statusLabels: Record<ReportStatus, string> = {
  STEP_FREE: 'Step-free',
  LIMITED: 'Limited access',
  BLOCKED: 'Blocked',
  WORKING: 'Working',
  INTERMITTENT: 'Intermittent',
  OUT_OF_SERVICE: 'Out of service',
  GENTLE: 'Gentle',
  MODERATE: 'Moderate',
  STEEP: 'Steep',
  VERY_STEEP: 'Very steep',
  USABLE: 'Usable',
  LOCKED: 'Locked',
  UNKNOWN: 'Unknown',
}

const optionsByType: Record<FeatureType, ReportStatus[]> = {
  ENTRANCE: ['STEP_FREE', 'LIMITED', 'BLOCKED', 'UNKNOWN'],
  LIFT: ['WORKING', 'INTERMITTENT', 'OUT_OF_SERVICE', 'UNKNOWN'],
  RAMP: ['GENTLE', 'MODERATE', 'STEEP', 'VERY_STEEP', 'UNKNOWN'],
  RESTROOM: ['USABLE', 'LOCKED', 'OUT_OF_SERVICE', 'UNKNOWN'],
}

function freshness(date: string | null) {
  if (!date) return 'No recent reports'
  const days = Math.max(0, Math.floor((Date.now() - new Date(date).getTime()) / 86_400_000))
  if (days === 0) return 'Confirmed today'
  if (days === 1) return 'Confirmed yesterday'
  return `Confirmed ${days} days ago`
}

function latestDate(features: FeatureSummary[]) {
  return features.reduce<string | null>((latest, feature) => {
    if (!feature.lastConfirmedAt) return latest
    if (!latest || feature.lastConfirmedAt > latest) return feature.lastConfirmedAt
    return latest
  }, null)
}

function placeTone(place: Place) {
  if (!place.features.length || place.features.every((feature) => feature.status === 'UNKNOWN')) {
    return 'uncertain'
  }
  if (place.features.some((feature) => ['BLOCKED', 'OUT_OF_SERVICE', 'LOCKED'].includes(feature.status))) {
    return 'barrier'
  }
  if (place.features.some((feature) => ['LIMITED', 'INTERMITTENT', 'STEEP', 'VERY_STEEP'].includes(feature.status))) {
    return 'limited'
  }
  return 'positive'
}

function MapCenter({ place }: { place: Place }) {
  const map = useMap()
  useEffect(() => {
    map.flyTo([place.latitude, place.longitude], Math.max(map.getZoom(), 14), { duration: 0.45 })
  }, [map, place.latitude, place.longitude])
  return null
}

function App() {
  const [places, setPlaces] = useState<Place[]>([])
  const [selectedId, setSelectedId] = useState('')
  const [search, setSearch] = useState('')
  const [activeFilter, setActiveFilter] = useState<'ALL' | FeatureType>('ALL')
  const [freshnessFilter, setFreshnessFilter] = useState<'ANY' | '7' | '30' | '90'>('ANY')
  const [isFilterOpen, setIsFilterOpen] = useState(false)
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false)
  const [reportFeature, setReportFeature] = useState<FeatureSummary | null>(null)
  const [isReportOpen, setIsReportOpen] = useState(false)
  const [reportType, setReportType] = useState<FeatureType>('ENTRANCE')
  const [reportLabel, setReportLabel] = useState('Main entrance')
  const [reportStatus, setReportStatus] = useState<ReportStatus>('UNKNOWN')
  const [reportNote, setReportNote] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [loadError, setLoadError] = useState('')
  const [isOfflinePreview, setIsOfflinePreview] = useState(false)
  const [successMessage, setSuccessMessage] = useState('')
  const [authToken, setAuthToken] = useState('')
  const [authEmail, setAuthEmail] = useState('')
  const [isModerator, setIsModerator] = useState(false)
  const [isModerationOpen, setIsModerationOpen] = useState(false)
  const [moderationReports, setModerationReports] = useState<ModerationReport[]>([])
  const [moderationAudit, setModerationAudit] = useState<ModerationAuditEntry[]>([])
  const [moderationView, setModerationView] = useState<'pending' | 'history'>('pending')
  const [isLoadingModeration, setIsLoadingModeration] = useState(false)
  const [moderatingReportId, setModeratingReportId] = useState<number | null>(null)
  const [moderationError, setModerationError] = useState('')
  const [isAuthOpen, setIsAuthOpen] = useState(false)
  const [authMode, setAuthMode] = useState<'login' | 'register'>('login')
  const [authEmailInput, setAuthEmailInput] = useState('')
  const [authPasswordInput, setAuthPasswordInput] = useState('')
  const [authError, setAuthError] = useState('')
  const [isAuthenticating, setIsAuthenticating] = useState(false)
  const [pendingReportRequest, setPendingReportRequest] = useState<FeatureSummary | 'new' | null>(null)

  useEffect(() => {
    let cancelled = false
    getPlaces()
      .then((result) => {
        if (cancelled) return
        setPlaces(result)
        setSelectedId(result[0]?.id ?? '')
        setIsOfflinePreview(false)
      })
      .catch((error: unknown) => {
        if (cancelled) return
        setPlaces(demoPlaces)
        setSelectedId(demoPlaces[0].id)
        setIsOfflinePreview(true)
        const message = error instanceof Error ? error.message : 'Could not reach the service.'
        setLoadError(`${message} Showing illustrative sample data; reporting is unavailable until the API connects.`)
      })
    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    if (isOfflinePreview) return
    const timer = window.setTimeout(() => {
      getPlaces(search)
        .then(setPlaces)
        .catch((error: unknown) => {
          setLoadError(error instanceof Error ? error.message : 'Could not search places.')
        })
    }, 250)
    return () => window.clearTimeout(timer)
  }, [isOfflinePreview, search])

  const visiblePlaces = useMemo(() => {
    const query = search.trim().toLowerCase()
    return places.filter((place) => {
      const matchesText = !query || `${place.name} ${place.address}`.toLowerCase().includes(query)
      const matchesFilter = activeFilter === 'ALL' || place.features.some((feature) => feature.type === activeFilter)
    const lastConfirmedAt = latestDate(place.features)
    const matchesFreshness = freshnessFilter === 'ANY'
      || (lastConfirmedAt !== null
        && Date.now() - new Date(lastConfirmedAt).getTime() <= Number(freshnessFilter) * 86_400_000)
    return matchesText && matchesFilter && matchesFreshness
    })
  }, [activeFilter, freshnessFilter, places, search])

  const selectedPlace = visiblePlaces.find((place) => place.id === selectedId) ?? visiblePlaces[0]

  useEffect(() => {
    if (selectedPlace && selectedPlace.id !== selectedId) setSelectedId(selectedPlace.id)
  }, [selectedId, selectedPlace])

  function openReport(feature: FeatureSummary) {
    if (!publicContributionsEnabled) return
    if (!authToken) {
      setPendingReportRequest(feature)
      setAuthMode('login')
      setAuthError('')
      setIsAuthOpen(true)
      return
    }
    openReportForm(feature)
  }

  function openReportForm(feature: FeatureSummary | null) {
    setReportFeature(feature)
    setIsReportOpen(true)
    const type = feature?.type ?? 'ENTRANCE'
    setReportType(type)
    setReportLabel(feature?.label ?? 'Main entrance')
    setReportStatus(optionsByType[type][0])
    setReportNote('')
    setSuccessMessage('')
  }

  function openNewFeatureReport() {
    if (!publicContributionsEnabled) return
    if (!authToken) {
      setPendingReportRequest('new')
      setAuthMode('login')
      setAuthError('')
      setIsAuthOpen(true)
      return
    }
    openReportForm(null)
  }

  function changeReportType(type: FeatureType) {
    setReportType(type)
    setReportLabel(type === 'ENTRANCE' ? 'Main entrance' : featureLabels[type])
    setReportStatus(optionsByType[type][0])
  }

  async function submitAuth(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setIsAuthenticating(true)
    setAuthError('')
    try {
      const result = await authenticate(authMode, authEmailInput, authPasswordInput)
      setAuthToken(result.token)
      setAuthEmail(result.email)
      setIsModerator(result.moderator)
      setAuthPasswordInput('')
      setIsAuthOpen(false)
      if (pendingReportRequest) {
        openReportForm(pendingReportRequest === 'new' ? null : pendingReportRequest)
        setPendingReportRequest(null)
      } else {
        setSuccessMessage(`Signed in as ${result.email}.`)
      }
    } catch (error) {
      setAuthError(error instanceof Error ? error.message : 'Authentication failed.')
    } finally {
      setIsAuthenticating(false)
    }
  }

  async function openModerationQueue() {
    if (!authToken || !isModerator) return
    setIsModerationOpen(true)
    setModerationView('pending')
    setIsLoadingModeration(true)
    setModerationError('')
    try {
      setModerationReports(await getPendingReports(authToken))
    } catch (error) {
      setModerationError(error instanceof Error ? error.message : 'Could not load moderation queue.')
    } finally {
      setIsLoadingModeration(false)
    }
  }

  async function openModerationHistory() {
    if (!authToken || !isModerator) return
    setModerationView('history')
    setIsLoadingModeration(true)
    setModerationError('')
    try {
      setModerationAudit(await getModerationAudit(authToken))
    } catch (error) {
      setModerationError(error instanceof Error ? error.message : 'Could not load moderation history.')
    } finally {
      setIsLoadingModeration(false)
    }
  }

  async function decideReport(reportId: number, decision: 'APPROVE' | 'REJECT') {
    if (!authToken) return
    setModeratingReportId(reportId)
    setModerationError('')
    try {
      await reviewReport(reportId, decision, authToken)
      setModerationReports((reports) => reports.filter((report) => report.id !== reportId))
      if (moderationView === 'history') {
        setModerationAudit(await getModerationAudit(authToken))
      }
      setPlaces(await getPlaces(search))
      setSuccessMessage(decision === 'APPROVE' ? 'Report approved and added to the map.' : 'Report rejected.')
    } catch (error) {
      setModerationError(error instanceof Error ? error.message : 'Could not review report.')
    } finally {
      setModeratingReportId(null)
    }
  }

  async function submitReport(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!publicContributionsEnabled || !selectedPlace || !isReportOpen || !authToken) return

    setIsSubmitting(true)
    try {
      if (reportFeature) {
        await createReport(selectedPlace.id, reportFeature.id, {
          status: reportStatus,
          note: reportNote.trim(),
        }, authToken)
      } else {
        await createFeatureReport(selectedPlace.id, authToken, {
          type: reportType,
          label: reportLabel.trim(),
          status: reportStatus,
          note: reportNote.trim(),
        })
      }
      setSuccessMessage('Thanks for helping keep this information current. It will appear on the map after moderator review.')
      setIsReportOpen(false)
      try {
        const updated = await getPlaces(search)
        setPlaces(updated)
        setIsOfflinePreview(false)
      } catch (error) {
        const message = error instanceof Error ? error.message : 'The map could not refresh.'
        setLoadError(`Your update was saved, but the map could not refresh: ${message}`)
      }
    } catch (error) {
      setLoadError(error instanceof Error ? error.message : 'Your report could not be submitted.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <a className="brand" href="#" aria-label="AccessMap home">
          <span className="brand-mark"><MapPin size={21} strokeWidth={2.4} /></span>
          <span>access<span className="brand-accent">map</span></span>
        </a>
        <nav className="desktop-nav" aria-label="Main navigation">
          <a className="nav-link active" href="#map">Explore map</a>
          <a className="nav-link" href="#how-it-works">How it works</a>
        </nav>
        <div className="topbar-actions">
          {publicAuthEnabled && authEmail ? (
            <>
              <span className="signed-in-email">{authEmail}</span>
              {publicContributionsEnabled && isModerator && (
                <button className="text-button" type="button" onClick={() => void openModerationQueue()}>
                  Moderation{moderationReports.length ? ` (${moderationReports.length})` : ''}
                </button>
              )}
              <button
                className="text-button"
                type="button"
                onClick={() => {
                  setAuthToken('')
                  setAuthEmail('')
                  setIsModerator(false)
                  setIsModerationOpen(false)
                  setIsReportOpen(false)
                  setSuccessMessage('You have been signed out.')
                }}
              >
                Sign out
              </button>
            </>
          ) : publicAuthEnabled ? (
            <>
              <button className="text-button" type="button" onClick={() => {
                setAuthMode('login')
                setAuthError('')
                setIsAuthOpen(true)
              }}>Log in</button>
              <button className="join-button" type="button" onClick={() => {
                setAuthMode('register')
                setAuthError('')
                setIsAuthOpen(true)
              }}>Join the community <ArrowUpRight size={15} /></button>
            </>
          ) : null}
          <button
            className="mobile-menu"
            type="button"
            aria-label={isMobileMenuOpen ? 'Close menu' : 'Open menu'}
            aria-expanded={isMobileMenuOpen}
            onClick={() => setIsMobileMenuOpen((open) => !open)}
          >
            {isMobileMenuOpen ? <X size={22} /> : <Menu size={22} />}
          </button>
        </div>
        {isMobileMenuOpen && (
          <nav className="mobile-nav" aria-label="Mobile navigation">
            <a href="#map" onClick={() => setIsMobileMenuOpen(false)}>Explore map</a>
            <a href="#how-it-works" onClick={() => setIsMobileMenuOpen(false)}>How it works</a>
            {publicAuthEnabled && authEmail ? (
              <>
                {publicContributionsEnabled && isModerator && (
                  <button type="button" onClick={() => {
                    setIsMobileMenuOpen(false)
                    void openModerationQueue()
                  }}>Moderation queue</button>
                )}
                <button type="button" onClick={() => {
                  setAuthToken('')
                  setAuthEmail('')
                  setIsModerator(false)
                  setIsModerationOpen(false)
                  setIsReportOpen(false)
                  setIsMobileMenuOpen(false)
                }}>Sign out ({authEmail})</button>
              </>
            ) : publicAuthEnabled ? (
              <>
                <button type="button" onClick={() => {
                  setAuthMode('login')
                  setAuthError('')
                  setIsAuthOpen(true)
                  setIsMobileMenuOpen(false)
                }}>Log in</button>
                <button type="button" onClick={() => {
                  setAuthMode('register')
                  setAuthError('')
                  setIsAuthOpen(true)
                  setIsMobileMenuOpen(false)
                }}>Join the community</button>
              </>
            ) : null}
          </nav>
        )}
      </header>

      <section className="intro" id="map">
        <div className="intro-copy">
          <div className="eyebrow"><span className="eyebrow-dot" /> DELHI ACCESSIBILITY, TOGETHER</div>
          <h1>Know before<br />you <em>go.</em></h1>
          <p>Real-world access changes. Find current community updates on entrances, lifts, ramps and restrooms.</p>
          <div className="intro-actions">
            {publicContributionsEnabled && (
              <button
                className="hero-report-button"
                type="button"
                disabled={isOfflinePreview || !selectedPlace}
                onClick={openNewFeatureReport}
              >
                <Plus size={17} /> Report an access issue
              </button>
            )}
            <a className="hero-how-link" href="#how-it-works">How it works <ArrowUpRight size={14} /></a>
          </div>
        </div>
        <div className="intro-note">
          <span className="note-icon"><ShieldCheck size={18} /></span>
          <span>Every report has a timestamp.<br /><strong>Freshness matters here.</strong></span>
        </div>
      </section>

      <section className="search-panel" aria-label="Find accessible places">
        <label className="search-field">
          <Search size={19} aria-hidden="true" />
          <span className="sr-only">Search a place or area</span>
          <input
            type="search"
            placeholder="Search a place or area in Delhi..."
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </label>
        <button
          className="filter-button"
          type="button"
          aria-label="Freshness filters"
          aria-expanded={isFilterOpen}
          aria-controls="freshness-filter-menu"
          onClick={() => setIsFilterOpen((open) => !open)}
        >
          <SlidersHorizontal size={17} /><span>Filters</span><ChevronDown size={15} />
        </button>
        <div
          className="filter-menu"
          id="freshness-filter-menu"
          role="group"
          aria-label="Filter by report freshness"
          hidden={!isFilterOpen}
        >
          <span className="filter-menu-title">Show reports from</span>
          {([
            ['ANY', 'Any age'],
            ['7', 'Last 7 days'],
            ['30', 'Last 30 days'],
            ['90', 'Last 90 days'],
          ] as const).map(([value, label]) => (
            <button
              className={`filter-menu-option ${freshnessFilter === value ? 'selected' : ''}`}
              key={value}
              type="button"
              aria-pressed={freshnessFilter === value}
              onClick={() => {
                setFreshnessFilter(value)
                setIsFilterOpen(false)
              }}
            >
              {label}
            </button>
          ))}
        </div>
      </section>

      {loadError && (
        <div className="notice error-notice" role="status">
          <AlertCircle size={17} />
          <span>{loadError}</span>
          <button type="button" aria-label="Dismiss message" onClick={() => setLoadError('')}><X size={16} /></button>
        </div>
      )}
      {successMessage && (
        <div className="notice success-notice" role="status">
          <Check size={17} /><span>{successMessage}</span>
          <button type="button" aria-label="Dismiss message" onClick={() => setSuccessMessage('')}><X size={16} /></button>
        </div>
      )}

      <div className="workspace">
        <aside className="results-panel" aria-label="Places and accessibility details">
          <div className="results-heading">
            <div>
              <div className="section-kicker">COMMUNITY REPORTS</div>
              <h2>Places nearby <span>{visiblePlaces.length}</span></h2>
            </div>
            <button className="icon-button" type="button" aria-label="Locate me" title="Location services coming soon"><LocateFixed size={18} /></button>
          </div>

          <div className="filter-chips" aria-label="Filter by feature">
            {(['ALL', 'ENTRANCE', 'LIFT', 'RAMP', 'RESTROOM'] as const).map((filter) => (
              <button
                className={`filter-chip ${activeFilter === filter ? 'selected' : ''}`}
                key={filter}
                type="button"
                aria-pressed={activeFilter === filter}
                onClick={() => setActiveFilter(filter)}
              >
                {filter === 'ALL' ? 'All' : featureLabels[filter]}
              </button>
            ))}
          </div>

          <div className="place-list">
            {visiblePlaces.map((place) => {
              const tone = placeTone(place)
              return (
                <button
                  className={`place-card ${place.id === selectedPlace?.id ? 'selected' : ''}`}
                  key={place.id}
                  type="button"
                  onClick={() => setSelectedId(place.id)}
                  aria-pressed={place.id === selectedPlace?.id}
                >
                  <span className={`place-dot ${tone}`} aria-hidden="true" />
                  <span className="place-card-copy">
                    <span className="place-name">{place.name}</span>
                    <span className="place-address">{place.category} · {place.address}</span>
                    <span className="place-meta"><Clock3 size={12} /> {freshness(latestDate(place.features))}</span>
                  </span>
                  <ArrowUpRight size={16} className="card-arrow" aria-hidden="true" />
                </button>
              )
            })}
            {visiblePlaces.length === 0 && <p className="empty-list">No matching places yet. Try another search.</p>}
          </div>

          {selectedPlace && (
            <section className="place-detail" aria-labelledby="place-detail-title">
              <div className="detail-topline">
                <span className="section-kicker">PLACE SNAPSHOT</span>
                <span className={`source-badge ${selectedPlace.demoData ? 'sample-badge' : 'osm-badge'}`}>
                  {selectedPlace.demoData ? 'SAMPLE' : 'OPENSTREETMAP'}
                </span>
              </div>
              <h3 id="place-detail-title">{selectedPlace.name}</h3>
              <p className="detail-address"><MapPin size={14} />{selectedPlace.address}</p>

              {selectedPlace.demoData && (
                <div className="sample-warning">
                  <AlertCircle size={15} />
                  <span>Illustrative data only. Confirm conditions with the venue before travelling.</span>
                </div>
              )}
              {!selectedPlace.demoData && (
                <div className="osm-note">
                  Place details are from OpenStreetMap. Only accessibility features reported by the community are shown here.
                </div>
              )}

              <div className="feature-list">
                {selectedPlace.features.map((feature) => {
                  const Icon = featureIcons[feature.type]
                  const tone = ['BLOCKED', 'OUT_OF_SERVICE', 'LOCKED'].includes(feature.status)
                    ? 'barrier'
                    : ['LIMITED', 'INTERMITTENT', 'STEEP', 'VERY_STEEP'].includes(feature.status)
                      ? 'limited'
                      : feature.status === 'UNKNOWN' ? 'uncertain' : 'positive'
                  return (
                    <article className="feature-row" key={feature.id}>
                      <span className={`feature-icon ${tone}`}><Icon size={17} /></span>
                      <div className="feature-copy">
                        <span className="feature-label">{feature.label}</span>
                        <span className={`feature-status ${tone}`}>{statusLabels[feature.status]}</span>
                        <span className="feature-freshness">{freshness(feature.lastConfirmedAt)} · {feature.confirmationCount} {feature.confirmationCount === 1 ? 'report' : 'reports'}</span>
                      </div>
                      <button
                        className="reconfirm-button"
                        type="button"
                        disabled={isOfflinePreview || !publicContributionsEnabled}
                        title={publicContributionsEnabled ? undefined : 'Reporting is disabled in this public demo.'}
                        onClick={() => openReport(feature)}
                        aria-label={`Update ${feature.label}`}
                      >
                        <Plus size={15} /> Update
                      </button>
                    </article>
                  )
                })}
                {selectedPlace.features.length === 0 && (
                  <p className="empty-features">No accessibility features have been reported for this place yet.</p>
                )}
              </div>
              <button
                className="add-place-button"
                type="button"
                disabled={isOfflinePreview || !publicContributionsEnabled}
                title={publicContributionsEnabled ? undefined : 'Reporting is disabled in this public demo.'}
                onClick={openNewFeatureReport}
              >
                <Plus size={16} /> Report an accessibility feature
              </button>
            </section>
          )}
        </aside>

        <section className="map-panel" aria-label="Map of accessibility reports">
          <div className="map-legend">
            <span className="legend-title">ACCESS SNAPSHOT</span>
            <span><i className="legend-dot positive" /> Mostly accessible</span>
            <span><i className="legend-dot limited" /> Some limitations</span>
            <span><i className="legend-dot barrier" /> Barrier reported</span>
            <span><i className="legend-dot uncertain" /> Unverified</span>
          </div>
          <MapContainer
            center={[28.635, 77.218]}
            zoom={13}
            zoomControl={false}
            scrollWheelZoom
            className="map"
          >
            <TileLayer
              attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
              url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            <ZoomControl position="bottomright" />
            {selectedPlace && <MapCenter place={selectedPlace} />}
            {visiblePlaces.map((place) => (
              <CircleMarker
                key={place.id}
                center={[place.latitude, place.longitude]}
                radius={place.id === selectedPlace?.id ? 12 : 9}
                pathOptions={{
                  color: '#fff',
                  weight: 3,
                  fillColor: placeTone(place) === 'positive'
                    ? '#2e8a6f'
                    : placeTone(place) === 'limited'
                      ? '#d08b24'
                      : placeTone(place) === 'barrier' ? '#c65d4e' : '#7a8793',
                  fillOpacity: 1,
                }}
                eventHandlers={{ click: () => setSelectedId(place.id) }}
              />
            ))}
          </MapContainer>
          <div className="map-caption"><Sparkles size={14} /> Fresh reports help everyone plan with more confidence.</div>
        </section>
      </div>

      <section className="how-it-works" id="how-it-works" aria-labelledby="how-it-works-title">
        <div className="how-heading">
          <span className="section-kicker">A CLEARER PICTURE, TOGETHER</span>
          <h2 id="how-it-works-title">How AccessMap works</h2>
          <p>Useful access details come from people who have been there. Here’s how to add what you know.</p>
        </div>
        <div className="how-steps">
          <article className="how-step">
            <span className="how-step-number">01</span>
            <h3>Choose a place</h3>
            <p>Search for a Delhi location or select its marker on the map to see the latest accessibility details.</p>
          </article>
          <article className="how-step">
            <span className="how-step-number">02</span>
            <h3>Share what you observed</h3>
            <p>Sign in, choose an entrance, lift, ramp, or restroom, and describe its current condition.</p>
          </article>
          <article className="how-step">
            <span className="how-step-number">03</span>
            <h3>Help keep details current</h3>
            <p>Reports are checked by a moderator before appearing on the map. Every update includes when it was confirmed.</p>
          </article>
        </div>
        {publicContributionsEnabled && (
          <button className="how-report-button" type="button" disabled={isOfflinePreview || !selectedPlace} onClick={openNewFeatureReport}>
            <Plus size={16} /> Share an accessibility report
          </button>
        )}
      </section>

      <footer className="page-footer">
        <span>
          {publicContributionsEnabled
            ? 'Community-reported information can change. Verify critical access needs with the venue.'
            : 'Public demo: contributions are disabled. Verify critical access needs with the venue.'}
        </span>
        <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">Place &amp; map data © OpenStreetMap contributors · ODbL</a>
      </footer>

      {isModerationOpen && (
        <div className="dialog-backdrop" role="presentation" onMouseDown={(event) => {
          if (event.target === event.currentTarget) setIsModerationOpen(false)
        }}>
          <section className="report-dialog moderation-dialog" role="dialog" aria-modal="true" aria-labelledby="moderation-title">
            <div className="dialog-header">
              <div>
                <span className="section-kicker">ACCESSMAP MODERATION</span>
                <h2 id="moderation-title">Review community reports</h2>
                <p>Approve reports you can verify; rejected reports stay off the public map.</p>
              </div>
              <button className="icon-button" type="button" aria-label="Close moderation queue" onClick={() => setIsModerationOpen(false)}><X size={19} /></button>
            </div>
            <div className="moderation-tabs" role="tablist" aria-label="Moderation views">
              <button
                className={moderationView === 'pending' ? 'selected' : ''}
                id="moderation-pending-tab"
                type="button"
                role="tab"
                aria-selected={moderationView === 'pending'}
                aria-controls="moderation-view"
                onClick={() => void openModerationQueue()}
              >
                Pending{moderationReports.length ? ` (${moderationReports.length})` : ''}
              </button>
              <button
                className={moderationView === 'history' ? 'selected' : ''}
                id="moderation-history-tab"
                type="button"
                role="tab"
                aria-selected={moderationView === 'history'}
                aria-controls="moderation-view"
                onClick={() => void openModerationHistory()}
              >
                Decision history
              </button>
            </div>
            {moderationError && <p className="moderation-error" role="alert">{moderationError}</p>}
            <div id="moderation-view" role="tabpanel" aria-labelledby={moderationView === 'pending' ? 'moderation-pending-tab' : 'moderation-history-tab'}>
              {isLoadingModeration ? (
                <p className="moderation-empty" role="status">
                  {moderationView === 'pending' ? 'Loading pending reports…' : 'Loading decision history…'}
                </p>
              ) : moderationView === 'pending' ? (
                moderationReports.length === 0 ? (
                  <p className="moderation-empty">No reports are waiting for review.</p>
                ) : (
                  <div className="moderation-list">
                    {moderationReports.map((report) => (
                      <article className="moderation-item" key={report.id}>
                        <div className="moderation-item-heading">
                          <strong>{report.featureLabel}</strong>
                          <span>{featureLabels[report.featureType]} · {statusLabels[report.status]}</span>
                        </div>
                        <p className="moderation-place">{report.placeName}</p>
                        {report.note && <p className="moderation-note">{report.note}</p>}
                        <p className="moderation-meta">
                          {report.reporterEmail ?? 'Legacy report'} · {new Date(report.reportedAt).toLocaleString()}
                        </p>
                        <div className="moderation-actions">
                          <button
                            className="moderation-reject"
                            type="button"
                            disabled={moderatingReportId !== null}
                            onClick={() => void decideReport(report.id, 'REJECT')}
                          >
                            {moderatingReportId === report.id ? 'Saving…' : 'Reject'}
                          </button>
                          <button
                            className="moderation-approve"
                            type="button"
                            disabled={moderatingReportId !== null}
                            onClick={() => void decideReport(report.id, 'APPROVE')}
                          >
                            {moderatingReportId === report.id ? 'Saving…' : 'Approve'}
                          </button>
                        </div>
                      </article>
                    ))}
                  </div>
                )
              ) : moderationAudit.length === 0 ? (
                <p className="moderation-empty">No moderation decisions have been recorded.</p>
              ) : (
                <div className="moderation-list">
                  {moderationAudit.map((entry) => (
                    <article className="moderation-item" key={entry.reportId}>
                      <div className="moderation-item-heading">
                        <strong>{entry.featureLabel}</strong>
                        <span className={entry.decision === 'APPROVED' ? 'audit-approved' : 'audit-rejected'}>
                          {entry.decision === 'APPROVED' ? 'Approved' : 'Rejected'}
                        </span>
                      </div>
                      <p className="moderation-place">
                        {entry.placeName} · {featureLabels[entry.featureType]} · {statusLabels[entry.reportStatus]}
                      </p>
                      <p className="moderation-meta">
                        By {entry.moderatorEmail} · {new Date(entry.reviewedAt).toLocaleString()}
                      </p>
                    </article>
                  ))}
                </div>
              )}
            </div>
          </section>
        </div>
      )}

      {isReportOpen && selectedPlace && (
        <div className="dialog-backdrop" role="presentation" onMouseDown={(event) => {
          if (event.target === event.currentTarget) setIsReportOpen(false)
        }}>
          <section className="report-dialog" role="dialog" aria-modal="true" aria-labelledby="report-title">
            <div className="dialog-header">
              <div>
                <span className="section-kicker">COMMUNITY UPDATE</span>
                <h2 id="report-title">{reportFeature ? `Update ${reportFeature.label.toLowerCase()}` : 'Report an accessibility feature'}</h2>
                <p>{selectedPlace.name}</p>
              </div>
              <button className="icon-button" type="button" aria-label="Close report form" onClick={() => setIsReportOpen(false)}><X size={19} /></button>
            </div>
            <form onSubmit={submitReport}>
              {!reportFeature && (
                <>
                  <label className="form-label" htmlFor="feature-type">What feature are you reporting?</label>
                  <select
                    id="feature-type"
                    value={reportType}
                    onChange={(event) => changeReportType(event.target.value as FeatureType)}
                    required
                  >
                    {Object.entries(featureLabels).map(([type, label]) => (
                      <option key={type} value={type}>{label}</option>
                    ))}
                  </select>
                  <label className="form-label" htmlFor="feature-label">Feature name</label>
                  <input
                    id="feature-label"
                    maxLength={80}
                    value={reportLabel}
                    onChange={(event) => setReportLabel(event.target.value)}
                    required
                  />
                </>
              )}

              <label className="form-label" htmlFor="report-status">What did you observe?</label>
              <select
                id="report-status"
                value={reportStatus}
                onChange={(event) => setReportStatus(event.target.value as ReportStatus)}
                required
              >
                {optionsByType[reportFeature?.type ?? reportType].map((status) => (
                  <option key={status} value={status}>{statusLabels[status]}</option>
                ))}
              </select>
              <label className="form-label" htmlFor="report-note">A helpful note <span>(optional, up to 280 characters)</span></label>
              <textarea
                id="report-note"
                maxLength={280}
                rows={3}
                placeholder="Share a detail that could help someone plan..."
                value={reportNote}
                onChange={(event) => setReportNote(event.target.value)}
              />
              <div className="form-hint"><AlertCircle size={14} /> Please only report what you personally observed.</div>
              <button className="submit-report" type="submit" disabled={isSubmitting || isOfflinePreview}>
                {isSubmitting ? 'Sending update…' : 'Share update'} <ArrowUpRight size={16} />
              </button>
            </form>
          </section>
        </div>
      )}

      {isAuthOpen && (
        <div className="dialog-backdrop" role="presentation" onMouseDown={(event) => {
          if (event.target === event.currentTarget) setIsAuthOpen(false)
        }}>
          <section className="report-dialog auth-dialog" role="dialog" aria-modal="true" aria-labelledby="auth-title">
            <div className="dialog-header">
              <div>
                <span className="section-kicker">COMMUNITY ACCOUNT</span>
                <h2 id="auth-title">{authMode === 'login' ? 'Welcome back' : 'Join the community'}</h2>
                <p>{publicContributionsEnabled
                  ? 'Sign in to submit accessibility reports.'
                  : 'Sign in or create an account to explore the demo.'}</p>
              </div>
              <button className="icon-button" type="button" aria-label="Close account form" onClick={() => setIsAuthOpen(false)}><X size={19} /></button>
            </div>
            <form onSubmit={submitAuth}>
              <label className="form-label" htmlFor="auth-email">Email address</label>
              <input
                id="auth-email"
                type="email"
                autoComplete="email"
                maxLength={254}
                value={authEmailInput}
                onChange={(event) => setAuthEmailInput(event.target.value)}
                required
              />
              <label className="form-label" htmlFor="auth-password">Password <span>(at least 12 characters)</span></label>
              <input
                id="auth-password"
                type="password"
                autoComplete={authMode === 'login' ? 'current-password' : 'new-password'}
                minLength={12}
                maxLength={72}
                value={authPasswordInput}
                onChange={(event) => setAuthPasswordInput(event.target.value)}
                required
              />
              {authError && <p className="auth-error" role="alert">{authError}</p>}
              <button className="submit-report" type="submit" disabled={isAuthenticating}>
                {isAuthenticating ? 'Please wait…' : authMode === 'login' ? 'Log in' : 'Create account'}
              </button>
            </form>
            <p className="auth-switch">
              {authMode === 'login' ? 'New to AccessMap?' : 'Already have an account?'}
              {' '}
              <button type="button" onClick={() => {
                setAuthMode((mode) => mode === 'login' ? 'register' : 'login')
                setAuthError('')
              }}>
                {authMode === 'login' ? 'Create an account' : 'Log in'}
              </button>
            </p>
            <p className="auth-security-note">Your password is securely hashed. This prototype does not yet include email verification or password recovery.</p>
          </section>
        </div>
      )}
    </main>
  )
}

export default App

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
import { authenticate, createFeatureReport, createReport, getPlaces } from './api'
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
  const [isAuthOpen, setIsAuthOpen] = useState(false)
  const [authMode, setAuthMode] = useState<'login' | 'register'>('login')
  const [authEmailInput, setAuthEmailInput] = useState('')
  const [authPasswordInput, setAuthPasswordInput] = useState('')
  const [authError, setAuthError] = useState('')
  const [isAuthenticating, setIsAuthenticating] = useState(false)

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
    if (!authToken) {
      setAuthMode('login')
      setAuthError('')
      setIsAuthOpen(true)
      return
    }
    setReportFeature(feature)
    setIsReportOpen(true)
    setReportType(feature.type)
    setReportLabel(feature.label)
    setReportStatus(optionsByType[feature.type][0])
    setReportNote('')
    setSuccessMessage('')
  }

  function openNewFeatureReport() {
    if (!authToken) {
      setAuthMode('login')
      setAuthError('')
      setIsAuthOpen(true)
      return
    }
    setReportFeature(null)
    setIsReportOpen(true)
    setReportType('ENTRANCE')
    setReportLabel('Main entrance')
    setReportStatus(optionsByType.ENTRANCE[0])
    setReportNote('')
    setSuccessMessage('')
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
      setAuthPasswordInput('')
      setIsAuthOpen(false)
      setSuccessMessage(`Signed in as ${result.email}.`)
    } catch (error) {
      setAuthError(error instanceof Error ? error.message : 'Authentication failed.')
    } finally {
      setIsAuthenticating(false)
    }
  }

  async function submitReport(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedPlace || !isReportOpen || !authToken) return

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
      setSuccessMessage('Thanks for helping keep this information current.')
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
          {authEmail ? (
            <>
              <span className="signed-in-email">{authEmail}</span>
              <button
                className="text-button"
                type="button"
                onClick={() => {
                  setAuthToken('')
                  setAuthEmail('')
                  setIsReportOpen(false)
                  setSuccessMessage('You have been signed out.')
                }}
              >
                Sign out
              </button>
            </>
          ) : (
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
          )}
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
            {authEmail ? (
              <button type="button" onClick={() => {
                setAuthToken('')
                setAuthEmail('')
                setIsReportOpen(false)
                setIsMobileMenuOpen(false)
              }}>Sign out ({authEmail})</button>
            ) : (
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
            )}
          </nav>
        )}
      </header>

      <section className="intro" id="map">
        <div className="intro-copy">
          <div className="eyebrow"><span className="eyebrow-dot" /> DELHI ACCESSIBILITY, TOGETHER</div>
          <h1>Know before<br />you <em>go.</em></h1>
          <p>Real-world access changes. Find current community updates on entrances, lifts, ramps and restrooms.</p>
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
                        disabled={isOfflinePreview}
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
                disabled={isOfflinePreview}
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

      <footer className="page-footer" id="how-it-works">
        <span>Community-reported information can change. Verify critical access needs with the venue.</span>
        <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">Place &amp; map data © OpenStreetMap contributors · ODbL</a>
      </footer>

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
                <p>Sign in to submit accessibility reports.</p>
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

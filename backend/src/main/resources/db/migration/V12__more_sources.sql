-- Fuentes adicionales verificadas el 2026-11-27 (API/feeds públicos, sin scraping de HTML).
INSERT INTO job_sources (code, type, display_name, enabled, terms_url, terms_notes, attribution_text,
                         attribution_required, commercial_use_allowed, min_interval_minutes, daily_request_cap) VALUES
('getonboard', 'PUBLIC_API', 'Get on Board', TRUE, 'https://www.getonbrd.com/api-doc',
 'API pública de Get on Board (LATAM). Enlazar a la oferta original y acreditar a Get on Board; postular en su sitio. Salarios en USD brutos/mes.',
 'Vía Get on Board', TRUE, FALSE, 60, 100),
('weworkremotely', 'PUBLIC_API', 'We Work Remotely', TRUE, 'https://weworkremotely.com/remote-job-rss-feed',
 'Feeds RSS públicos por categoría (back-end y full-stack). Enlazar a la oferta original y acreditar a We Work Remotely.',
 'Vía We Work Remotely', TRUE, FALSE, 360, 8),
('larajobs', 'PUBLIC_API', 'LaraJobs', TRUE, 'https://larajobs.com/feed',
 'Feed RSS público de LaraJobs (Laravel/PHP). El feed no trae descripción completa: se resume con los campos estructurados. Enlazar y acreditar.',
 'Vía LaraJobs', TRUE, FALSE, 360, 8),
('freelancer', 'PUBLIC_API', 'Freelancer.com', TRUE, 'https://developers.freelancer.com',
 'API pública de proyectos activos (lectura sin token). Son proyectos freelance (propuestas/bids), no empleos. Respetar los términos del desarrollador de Freelancer.',
 'Vía Freelancer.com', TRUE, FALSE, 60, 100);

INSERT INTO integration_settings (integration_code, enabled, config, daily_limit, monthly_limit, monthly_budget_usd) VALUES
('GETONBOARD',     TRUE, '{}', 100, NULL, NULL),
('WEWORKREMOTELY', TRUE, '{}', 8,   NULL, NULL),
('LARAJOBS',       TRUE, '{}', 8,   NULL, NULL),
('FREELANCER',     TRUE, '{}', 100, NULL, NULL);

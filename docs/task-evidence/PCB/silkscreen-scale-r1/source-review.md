# Root source review before production build

Reviewed renderer SHA256
`aca59f91fc318c27a59182affdb2d2e693ff15b6a42cdaf1579375bc885505cb`
and contract SHA256
`4c0afe43724b0e8564f3e673f065c6f849f6a4539fcd59a9732ba4d154b464a5`.
The worker supplied implementation/static checks; root owns runtime acceptance.

Physical silk draws before the no-provider early return. It uses the original
logical integer font with fractional canvas scaling, a projected double baseline,
explicit left/alphabetic text alignment and a scoped board clip. It retains
top-face policy, public text/power-label resolution, style and color. Provider
annotation generation and placement policy remain in their existing owners.

Actual canvas glyph bearings/ascent/descent are measured at the base context
before the board scale. Conservative projected ink with one raster-pixel padding
is clipped to the board and enters the existing obstacle list in that base frame.
The provider's existing CTM transforms it once. These obstacle bounds participate
in the existing projection/face/viewport/text/cache key. Physical text is redrawn
at its physical location rather than stored as a relocated screen label.

Nested `finally` blocks restore the text scale and outer canvas state; Java-side
`Graphics` font, font-size and color fields are restored to match the canvas.
No generator, layout, graph, replay, model epoch, instrument or input-handler
owner changed. Prior1466 audit permits exactly renderer/registry changes and one
new permanent contract;1464 prior inputs remain byte exact.

Root independently recalculated the literal overview, normal, inspection and
nonidentity-CTM rectangle expectations. Overview font0.525px is not clamped; the
native fixtures cover conservative bearings/ascent/descent, clipping and face
policy. These fixtures do not execute Canvas drawing or certify browser text
legibility. Actual JDK8/GWT and visible production Fit bench/Zoom checks remain
required. Browser observations and any further source change require their own
fresh acceptance; this review is bound to the hashes above.

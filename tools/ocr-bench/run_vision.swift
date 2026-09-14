// Runs Apple's Vision recognizer over the fixture set — the same engine an
// iOS build would use — and writes out/vision-<level>.json.
//
//   swiftc -O run_vision.swift -o .build/run_vision
//   .build/run_vision              # accurate, ru-RU + en-US
//   .build/run_vision --fast       # the realtime-oriented level
//
// Boxes are converted from Vision's normalized, bottom-left origin to
// top-left pixels, so every engine in this bench reports them the same way.

import Foundation
import Vision
import AppKit

struct Line: Codable {
    let text: String
    let confidence: Float
    let box: [Double]
}

struct Image: Codable {
    let image: String
    let ms: Double
    let lines: [Line]
}

struct Output: Codable {
    let engine: String
    let config: String
    let images: [Image]
}

func recognize(_ url: URL, accurate: Bool, languages: [String]) -> Image? {
    guard let loaded = NSImage(contentsOf: url),
          let cg = loaded.cgImage(forProposedRect: nil, context: nil, hints: nil) else {
        FileHandle.standardError.write("cannot load \(url.path)\n".data(using: .utf8)!)
        return nil
    }
    let w = Double(cg.width), h = Double(cg.height)

    let request = VNRecognizeTextRequest()
    request.recognitionLevel = accurate ? .accurate : .fast
    request.recognitionLanguages = languages
    request.usesLanguageCorrection = true

    let handler = VNImageRequestHandler(cgImage: cg, options: [:])
    let start = DispatchTime.now()
    do { try handler.perform([request]) } catch {
        FileHandle.standardError.write("failed \(url.lastPathComponent): \(error)\n".data(using: .utf8)!)
        return nil
    }
    let ms = Double(DispatchTime.now().uptimeNanoseconds - start.uptimeNanoseconds) / 1_000_000

    let lines: [Line] = (request.results ?? []).compactMap { obs in
        guard let top = obs.topCandidates(1).first else { return nil }
        let b = obs.boundingBox
        let x0 = Double(b.origin.x) * w
        let x1 = Double(b.origin.x + b.size.width) * w
        let y0 = (1 - Double(b.origin.y + b.size.height)) * h
        let y1 = (1 - Double(b.origin.y)) * h
        return Line(text: top.string, confidence: top.confidence, box: [x0, y0, x1, y1])
    }
    return Image(image: url.deletingPathExtension().lastPathComponent, ms: ms, lines: lines)
}

// --- args -------------------------------------------------------------------
var accurate = true
var languages = ["ru-RU", "en-US"]
var argv = CommandLine.arguments
var i = 1
while i < argv.count {
    switch argv[i] {
    case "--fast":  accurate = false
    case "--langs": i += 1; languages = argv[i].split(separator: ",").map(String.init)
    default: break
    }
    i += 1
}

let here = URL(fileURLWithPath: CommandLine.arguments[0])
    .deletingLastPathComponent().deletingLastPathComponent()
let imgDir = here.appendingPathComponent("img")
let outDir = here.appendingPathComponent("out")
try? FileManager.default.createDirectory(at: outDir, withIntermediateDirectories: true)

let files = (try? FileManager.default.contentsOfDirectory(at: imgDir, includingPropertiesForKeys: nil))?
    .filter { $0.pathExtension == "png" }.sorted { $0.path < $1.path } ?? []
if files.isEmpty {
    FileHandle.standardError.write("no PNGs in \(imgDir.path)\n".data(using: .utf8)!)
    exit(1)
}

let images = files.compactMap { recognize($0, accurate: accurate, languages: languages) }
for im in images {
    FileHandle.standardError.write("\(im.image)  [\(Int(im.ms)) ms]\n".data(using: .utf8)!)
    for l in im.lines {
        FileHandle.standardError.write("    \(String(format: "%.2f", l.confidence))  \(l.text)\n".data(using: .utf8)!)
    }
}

let level = accurate ? "accurate" : "fast"
let out = Output(engine: "Apple Vision",
                 config: "\(level), \(languages.joined(separator: "+"))",
                 images: images)
let encoder = JSONEncoder()
encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
let dest = outDir.appendingPathComponent("vision-\(level).json")
try encoder.encode(out).write(to: dest)
FileHandle.standardError.write("\nwrote \(dest.path)\n".data(using: .utf8)!)

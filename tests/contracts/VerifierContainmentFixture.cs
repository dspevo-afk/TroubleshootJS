using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Globalization;
using System.IO;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Web.Script.Serialization;

internal static class VerifierContainmentFixture
{
    private const uint JobObjectQuery = 0x0004;

    [DllImport("kernel32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern IntPtr OpenJobObjectW(uint desiredAccess,
        [MarshalAs(UnmanagedType.Bool)] bool inheritHandle, string name);

    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool IsProcessInJob(IntPtr processHandle,
        IntPtr jobHandle, [MarshalAs(UnmanagedType.Bool)] out bool result);

    [DllImport("kernel32.dll")]
    private static extern IntPtr GetCurrentProcess();

    [DllImport("kernel32.dll", SetLastError = true)]
    [return: MarshalAs(UnmanagedType.Bool)]
    private static extern bool CloseHandle(IntPtr handle);

    private static bool IsInExpectedJob(string jobName)
    {
        IntPtr job = OpenJobObjectW(JobObjectQuery, false, jobName);
        if (job == IntPtr.Zero || job == new IntPtr(-1))
            throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());
        try
        {
            bool inJob;
            if (!IsProcessInJob(GetCurrentProcess(), job, out inJob))
                throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());
            return inJob;
        }
        finally
        {
            if (!CloseHandle(job))
                throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error());
        }
    }

    private static string Sha256File(string path)
    {
        using (FileStream stream = File.OpenRead(path))
        using (SHA256 sha = SHA256.Create())
        {
            byte[] digest = sha.ComputeHash(stream);
            StringBuilder text = new StringBuilder(digest.Length * 2);
            foreach (byte value in digest)
                text.Append(value.ToString("x2", CultureInfo.InvariantCulture));
            return text.ToString();
        }
    }

    private static string EscapeNonAscii(string json)
    {
        StringBuilder ascii = new StringBuilder(json.Length);
        foreach (char value in json)
        {
            if (value <= 0x7f)
                ascii.Append(value);
            else
                ascii.Append("\\u").Append(((int)value).ToString("x4", CultureInfo.InvariantCulture));
        }
        return ascii.ToString();
    }

    private static void WriteState(string stateDirectory, string mode,
        string nonce, string jobName, out string imagePath)
    {
        Dictionary<string, object> record = new Dictionary<string, object>(StringComparer.Ordinal);
        long creationFileTime;
        int processId;
        string[] argv = Environment.GetCommandLineArgs();
        string commandLine = Environment.CommandLine;
        using (Process process = Process.GetCurrentProcess())
        {
            processId = process.Id;
            creationFileTime = process.StartTime.ToUniversalTime().ToFileTimeUtc();
            imagePath = process.MainModule.FileName;
        }
        record["nonce"] = nonce;
        record["pid"] = processId;
        record["creationFileTime"] = creationFileTime;
        record["imagePath"] = imagePath;
        record["imageSha256"] = Sha256File(imagePath);
        record["argv"] = argv;
        record["commandLine"] = commandLine;
        record["mode"] = mode;
        record["inExpectedJob"] = IsInExpectedJob(jobName);

        JavaScriptSerializer serializer = new JavaScriptSerializer();
        string json = EscapeNonAscii(serializer.Serialize(record)) + "\n";
        byte[] bytes = Encoding.ASCII.GetBytes(json);
        string path = Path.Combine(stateDirectory, mode + ".state.json");
        using (FileStream output = new FileStream(path, FileMode.CreateNew,
            FileAccess.Write, FileShare.Read))
        {
            output.Write(bytes, 0, bytes.Length);
            output.Flush();
        }
    }

    private static int ParseBoundedDecimal(string text, int minimum,
        int maximum, string name)
    {
        if (String.IsNullOrEmpty(text))
            throw new ArgumentException("A containment fixture number is empty.", name);
        foreach (char value in text)
        {
            if (value < '0' || value > '9')
                throw new ArgumentException("A containment fixture number is not strict decimal.", name);
        }
        int parsed;
        if (!Int32.TryParse(text, NumberStyles.None, CultureInfo.InvariantCulture,
                out parsed) || parsed < minimum || parsed > maximum ||
            !String.Equals(parsed.ToString(CultureInfo.InvariantCulture), text,
                StringComparison.Ordinal))
            throw new ArgumentOutOfRangeException(name,
                "A containment fixture number is outside its bound or not canonical decimal.");
        return parsed;
    }

    private static void WritePattern(Stream output, byte value, int count)
    {
        byte[] chunk = new byte[4096];
        for (int index = 0; index < chunk.Length; index++)
            chunk[index] = value;
        int remaining = count;
        while (remaining > 0)
        {
            int length = Math.Min(chunk.Length, remaining);
            output.Write(chunk, 0, length);
            output.Flush();
            remaining -= length;
        }
        output.Flush();
    }

    private sealed class PatternWriter
    {
        private readonly Stream output;
        private readonly byte value;
        private readonly int count;
        public Exception Failure;

        public PatternWriter(Stream stream, byte pattern, int byteCount)
        {
            output = stream;
            value = pattern;
            count = byteCount;
        }

        public void Run()
        {
            try { WritePattern(output, value, count); }
            catch (Exception error) { Failure = error; }
        }
    }

    private static void WriteDuplex(int stdoutCount, int stderrCount)
    {
        PatternWriter stdoutWriter = new PatternWriter(
            Console.OpenStandardOutput(), (byte)'O', stdoutCount);
        PatternWriter stderrWriter = new PatternWriter(
            Console.OpenStandardError(), (byte)'E', stderrCount);
        Thread stdoutThread = new Thread(stdoutWriter.Run);
        Thread stderrThread = new Thread(stderrWriter.Run);
        bool stdoutStarted = false;
        bool stderrStarted = false;
        Exception startFailure = null;
        try
        {
            stdoutThread.Start();
            stdoutStarted = true;
            stderrThread.Start();
            stderrStarted = true;
        }
        catch (Exception error) { startFailure = error; }
        finally
        {
            if (stdoutStarted) stdoutThread.Join();
            if (stderrStarted) stderrThread.Join();
        }
        if (startFailure != null)
            throw new InvalidOperationException(
                "Could not start both containment fixture output writers.", startFailure);
        if (stdoutWriter.Failure != null)
            throw new IOException("Containment fixture stdout writer failed.",
                stdoutWriter.Failure);
        if (stderrWriter.Failure != null)
            throw new IOException("Containment fixture stderr writer failed.",
                stderrWriter.Failure);
    }

    private static int Main(string[] args)
    {
        if (args == null || args.Length < 4 || args.Length > 7)
            throw new ArgumentException("Expected a supported containment fixture mode and its arguments.");
        string mode = args[0];
        string stateDirectory = args[1];
        string nonce = args[2];
        string jobName = args[3];
        if (String.IsNullOrWhiteSpace(nonce) || String.IsNullOrWhiteSpace(jobName) ||
            !Directory.Exists(stateDirectory))
            throw new ArgumentException("Containment fixture identity or state directory is invalid.");

        string imagePath;
        if (mode == "parent" || mode == "parent-writer" ||
            mode == "descendant" || mode == "sentinel")
        {
            bool startsChild = mode == "parent" || mode == "parent-writer";
            if ((startsChild && args.Length != 5) ||
                (!startsChild && args.Length != 4))
                throw new ArgumentException("Only parent modes accept a child argument string.");
            WriteState(stateDirectory, mode, nonce, jobName, out imagePath);
            if (mode == "parent")
            {
                ProcessStartInfo childInfo = new ProcessStartInfo();
                childInfo.FileName = imagePath;
                childInfo.Arguments = args[4];
                childInfo.WorkingDirectory = stateDirectory;
                childInfo.UseShellExecute = false;
                childInfo.CreateNoWindow = true;
                using (Process child = Process.Start(childInfo))
                {
                    if (child == null || child.Id <= 0)
                        throw new InvalidOperationException("The contained descendant did not start.");
                    Thread.Sleep(30000);
                    child.Refresh();
                }
            }
            else if (mode == "parent-writer")
            {
                ProcessStartInfo childInfo = new ProcessStartInfo();
                childInfo.FileName = imagePath;
                childInfo.Arguments = args[4];
                childInfo.WorkingDirectory = stateDirectory;
                childInfo.UseShellExecute = false;
                childInfo.CreateNoWindow = true;
                // Select the framework standard-handle setup without redirecting
                // either output: the child must use the inherited pipe writers.
                childInfo.RedirectStandardInput = true;
                using (Process child = Process.Start(childInfo))
                {
                    if (child == null || child.Id <= 0)
                        throw new InvalidOperationException("The contained writer child did not start.");
                    child.StandardInput.Close();
                }
            }
            else { Thread.Sleep(30000); }
            return 0;
        }

        if (mode == "emit" || mode == "hold")
        {
            if (args.Length != 7)
                throw new ArgumentException("Emit and hold modes require both byte counts and an exit code.");
            int stdoutCount = ParseBoundedDecimal(args[4], 0, 1048576,
                "stdoutCount");
            int stderrCount = ParseBoundedDecimal(args[5], 0, 1048576,
                "stderrCount");
            int exitCode = ParseBoundedDecimal(args[6], 0, 255, "exitCode");
            WriteState(stateDirectory, mode, nonce, jobName, out imagePath);
            WriteDuplex(stdoutCount, stderrCount);
            if (mode == "hold") Thread.Sleep(30000);
            return exitCode;
        }
        throw new ArgumentException("Unknown containment fixture mode.");
    }
}

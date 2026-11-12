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

    private static int Main(string[] args)
    {
        if (args == null || args.Length < 4 || args.Length > 5)
            throw new ArgumentException("Expected mode, state directory, nonce, job name, and optional child argument string.");
        string mode = args[0];
        string stateDirectory = args[1];
        string nonce = args[2];
        string jobName = args[3];
        if (mode != "parent" && mode != "descendant" && mode != "sentinel")
            throw new ArgumentException("Unknown containment fixture mode.");
        if (String.IsNullOrWhiteSpace(nonce) || String.IsNullOrWhiteSpace(jobName) ||
            !Directory.Exists(stateDirectory))
            throw new ArgumentException("Containment fixture identity or state directory is invalid.");
        if ((mode == "parent") != (args.Length == 5))
            throw new ArgumentException("Only parent mode accepts a descendant argument string.");

        string imagePath;
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
        else
        {
            Thread.Sleep(30000);
        }
        return 0;
    }
}
